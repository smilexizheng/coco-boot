package com.coco.boot.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.coco.boot.common.R;
import com.coco.boot.config.CoCoConfig;
import com.coco.boot.config.RiskContrConfig;
import com.coco.boot.entity.ServiceStatus;
import com.coco.boot.interceptor.ChatInterceptor;
import com.coco.boot.pojo.Conversation;
import com.coco.boot.service.CoCoPilotService;
import com.coco.boot.task.CoCoTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jodd.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.*;
import org.redisson.api.map.event.EntryExpiredListener;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.coco.boot.constant.RiskContrConstant.*;
import static com.coco.boot.constant.SysConstant.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@AllArgsConstructor
@Service
@Slf4j
@EnableAsync
public class CoCoPilotServiceImpl implements CoCoPilotService {


    private final RestTemplate rest;

    private final RedissonClient redissonClient;

    private final CoCoConfig coCoConfig;
    private final RiskContrConfig rcConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final JSONObject NO_KEYS = JSON.parseObject("{\"message\": \"No keys\"}");
    private static final JSONObject CODE_429 = JSON.parseObject("{\"message\": \"Rate limit\"}");

    @Override
    public R<String> uploadGhu(String data) {
        if (!StringUtils.hasLength(data)) {
            return R.fail();
        }
        Set<String> keys;
        try {
            JSONObject json = JSONObject.parseObject(data);
            keys = json.keySet();
        } catch (Exception e) {
            keys = new HashSet<>(Arrays.asList(data.split("%0A|\\r?\\n")));
        }
        //不符合的数据记录
        Map<String, String> map = new HashMap<>();
        // 处理数据换行
        RSet<String> aliveSet = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        List<CompletableFuture> futures = new ArrayList<>();

        for (String key : keys) {
            if (!key.startsWith("gh") && key.length() < 300) {
                map.put(key, "格式错误");
                continue;
            }

            if (aliveSet.contains(key)) {
                map.put(key, "重复添加");
                log.info("重复添加GHU:{}", key);
                continue;
            }
            CompletableFuture<HttpResponse<String>> future = CoCoTask.AliveTest(key, coCoConfig);
            CompletableFuture<Void> voidCompletableFuture = future.thenAccept(response -> {
                int statusCode = response.statusCode();
                if (statusCode == HttpStatus.OK.value()) {
                    map.put(key, "存活");
                    aliveSet.add(key);
                } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    String retryAfter = response.headers().firstValue(HEADER_RETRY).orElse("100");
                    map.put(key, "限流");
                    setCoolkey(key, retryAfter);
                    log.info("upload 存活校验限流: {}, 返回: {}", key, response.body());
                } else {
                    map.put(key, "失效");
                    log.warn("upload 存活校验失效: {}:{}", statusCode, key);
                }
            });
            futures.add(voidCompletableFuture);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return R.success("操作完成", map.toString());
    }


    @Override
    public ModelAndView token() {
        String encodedRedirectUri = URLUtil.encode(coCoConfig.getRedirectUri());
        String stateKey = IdUtil.simpleUUID();
        // 存储 state 值到 KV 中，以便稍后验证
        // 这里可以使用 Redis、Memcached 或者其他存储方式
        // 存活时间设置为5分钟
        RBucket<Integer> state = redissonClient.getBucket(TOKEN_STATE + stateKey);
        state.set(1, Duration.ofMinutes(coCoConfig.getExpirationTtl()));
        String authUrl = coCoConfig.getAuthorizationEndpoint() + "?client_id=" + coCoConfig.getClientId() + "&state=" + stateKey + "&redirect_uri=" + encodedRedirectUri + "&response_type=code&scope=read";

        return new ModelAndView(new RedirectView(authUrl, true, false));
    }

    @Override
    public ResponseEntity<String> callback(String code, String state) {
        RBucket<Object> bucket = redissonClient.getBucket(TOKEN_STATE + state);
        if (!bucket.isExists()) {
            // 403
            return ResponseEntity.status(FORBIDDEN).build();
        }
        bucket.delete();


        String auth = "Basic " + Base64.encode(coCoConfig.getClientId() + ':' + coCoConfig.getClientSecret());

        // 构造请求 body
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", auth);

        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "authorization_code");
        requestBody.add("code", code);
        requestBody.add("redirect_uri", coCoConfig.getRedirectUri());

        // 发送请求并解析响应
        ResponseEntity<JSONObject> response = rest.postForEntity(coCoConfig.getTokenEndpoint(), new HttpEntity<>(requestBody, headers), JSONObject.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            log.error("获取Token失败");
            return new ResponseEntity<>("{\"message\": \"Token Get Error\", \"data\": \"\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        JSONObject tokenData = response.getBody();
        assert tokenData != null;
        String accessToken = tokenData.getString("access_token");
        if (StringUtil.isBlank(accessToken)) {
            return new ResponseEntity<>("Error fetching token", HttpStatus.INTERNAL_SERVER_ERROR);
        }


        // 使用 access_token 获取用户信息
        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);
        ResponseEntity<JSONObject> responseEntity = rest.exchange(coCoConfig.getUserEndpoint(), HttpMethod.GET, new HttpEntity<>(userInfoHeaders), JSONObject.class);
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            log.error("获取用户信息失败");
            return new ResponseEntity<>("{\"message\": \"User Info Get Error\", \"data\": \"\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        JSONObject userInfo = responseEntity.getBody();
        assert userInfo != null;
        String userId = userInfo.getString("id");

        //检查是否禁止访问
        if (redissonClient.getBucket(RC_BAN + userId).isExists()) {
            return new ResponseEntity<>("You have been banned", HttpStatus.UNAUTHORIZED);
        }
        if (redissonClient.getBucket(RC_TEMPORARY_BAN + userId).isExists()) {
            return new ResponseEntity<>("You have been marked, please try again in a few hours", HttpStatus.UNAUTHORIZED);
        }
        //登陆次数
        if (redissonClient.getAtomicLong(RC_GET_TOKEN_NUM + userId).incrementAndGet() > rcConfig.getGetTokenNum()) {
            return new ResponseEntity<>("You Can Try again tomorrow", HttpStatus.UNAUTHORIZED);
        }

        // 检测用户信息         0级用户直接ban
        int trustLevel = userInfo.getIntValue("trust_level");
        boolean active = userInfo.getBooleanValue("active");
        if (!active || trustLevel < coCoConfig.getUserLevel()) {
            log.warn("{} trust_level is 0 or  is not active ", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Your trust_level is 0 or  is not active ");
        }

        String userInfoJsonString = JSON.toJSONString(userInfo);
        RBucket<String> users = redissonClient.getBucket(LINUX_DO_USER_ID + userId);
        users.set(userInfoJsonString);
        //虚拟本系统用户信息- 通过此获取到linux userId ，继而可以获取 linux的tokens
        String token = IdUtil.simpleUUID();
        RBucket<String> cocoAuth = redissonClient.getBucket(SYS_USER_ID + token);
        cocoAuth.set(userInfoJsonString, Duration.ofHours(coCoConfig.getUserTokenExpire()));
        return new ResponseEntity<>("{\"message\": \"Token Get Success\", \"data\": \"" + token + "\"}", HttpStatus.OK);
    }


    @Override
    public ResponseEntity<?> chat(Conversation requestBody, String auth, String path, HttpServletResponse response) {
        JSONObject userInfo = ChatInterceptor.tl.get();
        auth = auth.substring("Bearer ".length());
        String userId = userInfo.getString("id");
        int trustLevel = userInfo.getIntValue("trust_level");
        RSet<String> ghuAliveKey = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);
        if (!ghuAliveKey.isExists()) {
            return ResponseEntity.ok(NO_KEYS);
        }
        // 根据用户信任级别限流
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
        if (!rateLimiter.isExists()) {
            rateLimiter.trySetRate(RateType.OVERALL, ((long) coCoConfig.getUserFrequencyDegree() * trustLevel), coCoConfig.getUserRateTime(), RateIntervalUnit.MINUTES);
            rateLimiter.expireAsync(Duration.ofHours(coCoConfig.getUserTokenExpire()));
        }
        String tokenKey = DigestUtils.md5DigestAsHex((auth + userId).getBytes());
        if (rateLimiter.tryAcquire()) {
            // 调用 handleProxy 方法并获取响应
            ResponseEntity<?> result = getBaseProxyResponse(requestBody, path, ghuAliveKey, response);
            if (result.getStatusCode().is2xxSuccessful()) {
                //成功访问
                long tokenSuccess = redissonClient.getAtomicLong(RC_TOKEN_SUCCESS_REQ + tokenKey).incrementAndGet();
                if (tokenSuccess > ((long) rcConfig.getTokenMaxReq() * trustLevel)) {
                    redissonClient.getBucket(SYS_USER_ID + auth).deleteAsync();
                }
                long userSuccess = redissonClient.getAtomicLong(RC_USER_SUCCESS_REQ + userId).incrementAndGet();
                if (userSuccess > ((long) rcConfig.getUserMaxReq() * trustLevel)) {
                    redissonClient.getBucket(RC_TEMPORARY_BAN + userId).set(true, Duration.ofHours(rcConfig.getUserMaxTime()));
                }
            }
            return result;
        } else {
            long l = redissonClient.getAtomicLong(RC_USER_TOKEN_LIMIT_NUM + tokenKey).incrementAndGet();
            RAtomicLong userLimit = redissonClient.getAtomicLong(RC_USER_LIMIT_NUM + userId);
            if (l > rcConfig.getRejectTimeNum()) {
                userLimit.incrementAndGet();
                redissonClient.getBucket(SYS_USER_ID + auth).deleteAsync();
                redissonClient.getBucket(RC_TEMPORARY_BAN + userId).set(true, Duration.ofHours(rcConfig.getRejectTime()));
            } else if (l >= rcConfig.getTokenInvalidNum() && l <= rcConfig.getRejectTimeNum()) {
                rateLimiter.trySetRate(RateType.OVERALL, ((long) (coCoConfig.getUserFrequencyDegree() * 0.5)), coCoConfig.getUserRateTime(), RateIntervalUnit.MINUTES);
            }

            if (userLimit.isExists() && userLimit.get() > rcConfig.getBanNum()) {
                redissonClient.getBucket(SYS_USER_ID + auth).deleteAsync();
                redissonClient.getBucket(RC_BAN + userId).set(true);
            }


            log.warn("用户ID:{}，trustLevel:{}，token:{}被限流使用", userId, trustLevel, auth);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CODE_429);
        }

    }

    @Override
    public ServiceStatus getServiceStatus() {
        ServiceStatus status = new ServiceStatus();
        int aliveCount = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE).size();
        int noAliveCount = redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE).size();
        long noAliveTotal = redissonClient.getAtomicLong(COUNT_NO_ALIVE_KEY).get();
        status.setAliveCount(aliveCount);
        status.setNoAliveCount(noAliveCount);
        status.setNoAliveTotal(noAliveTotal);
        status.setGhuCount(aliveCount + noAliveCount + noAliveTotal);
        return status;
    }


    @NotNull
    private ResponseEntity<?> getBaseProxyResponse(Conversation requestBody, String path, RSet<String> ghuAliveKey, HttpServletResponse response) {
        int i = 0;
        while (i < 2) {
            String ghu = getGhu(ghuAliveKey);
            if (StringUtil.isBlank(ghu)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CODE_429);
            }
            log.info("{}可用令牌数量，当前选择{}", ghuAliveKey.size(), ghu);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
            headers.set("Authorization", "Bearer " + ghu);
            StopWatch sw = new StopWatch();
            sw.start("进入代理");
            ResponseEntity<String> result = null;

            if (!requestBody.getStream()) {
                // 非流式处理使用postForEntity方法
                result = rest.postForEntity(
                        coCoConfig.getBaseProxy() + path,
                        new HttpEntity<>(requestBody, headers),
                        String.class);
            } else {
                // 流式处理使用execute方法
                try {
                    rest.execute(
                            coCoConfig.getBaseProxy() + path,
                            HttpMethod.POST,
                            requestCallback -> {
                                HttpHeaders requestHeaders = requestCallback.getHeaders();
                                requestHeaders.setContentType(MediaType.APPLICATION_JSON);
                                requestHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
                                requestHeaders.set("Authorization", "Bearer " + ghu);
                                String jsonBody = convertObjectToJson(requestBody);
                                requestCallback.getBody().write(jsonBody.getBytes(StandardCharsets.UTF_8));
                            },
                            responseExtractor -> {
                                responseExtractor.getHeaders().forEach((key, value) -> {
                                    response.setHeader(key, String.join(",", value));
                                });
                                response.setStatus(responseExtractor.getRawStatusCode());
                                response.setHeader("Content-Type", "application/stream+json");
                                StreamUtils.copy(responseExtractor.getBody(), response.getOutputStream());
                                //ghu使用成功次数
                                RAtomicLong atomicLong = redissonClient.getAtomicLong(USING_GHU + ghu);
                                atomicLong.incrementAndGet();
                                return null;
                            }
                    );
                    return ResponseEntity.ok().build(); // 表示流式处理成功
                } catch (Exception e) {
                    log.error("流式处理异常", e);
                    // 根据实际情况处理异常，例如设置重试或返回错误响应
                }
            }
            sw.stop();
            log.info(sw.prettyPrint(TimeUnit.SECONDS));
            if (result == null) {
                i++;
                continue;
            }
            if (result.getStatusCode().is2xxSuccessful()) {
                //ghu使用成功次数
                RAtomicLong atomicLong = redissonClient.getAtomicLong(USING_GHU + ghu);
                atomicLong.incrementAndGet();
                // 客户端请求全部数据
                return result;
            } else {
                ghuAliveKey.remove(ghu);
                if (result.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    String retryAfter = result.getHeaders().getFirst(HEADER_RETRY);
                    setCoolkey(ghu, retryAfter);
                } else {
                    redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE).addAsync(ghu);
                }
                i++;
            }
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CODE_429);
    }


    /**
     * 限流随机现有GHU
     * 递归改成循环调用。防止栈溢出
     */
    public String getGhu(RSet<String> ghuAliveKey) {
        RRateLimiter rateLimiter;
        int retryCount = 0;
        while (retryCount <= 10) {
            String ghu = ghuAliveKey.random();
            rateLimiter = redissonClient.getRateLimiter(GHU_RATE_LIMITER + ghu);
            if (!rateLimiter.isExists()) {
                RateIntervalUnit timeUnit = RateIntervalUnit.SECONDS;
                rateLimiter.trySetRate(RateType.OVERALL, coCoConfig.getFrequencyDegree(), coCoConfig.getFrequencyTime(), timeUnit);
                rateLimiter.expireAsync(Duration.ofSeconds(2));
            }
            if (rateLimiter.tryAcquire()) {
                return ghu;
            } else {
                log.info("{} 被限流使用", ghu);
                retryCount++;
            }
        }
        return null;
    }

    private void setCoolkey(String ghu, String retryAfter) {
        // 默认 600秒
        long time = 120;
        if (StringUtil.isNotBlank(retryAfter)) {
            try {
                time = Long.parseLong(retryAfter);
            } catch (NumberFormatException ignored) {
            }
        }

        if (time > 1000) {
            redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE).addAsync(ghu);
        } else {
            RMapCache<String, Integer> collingMap = redissonClient.getMapCache(GHU_COOLING_KEY);
            if (!collingMap.isExists()) {
                collingMap.addListener((EntryExpiredListener<String, Integer>) event -> {
                    // expired key
                    redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE).add(event.getKey());
                });
            }
            collingMap.put(ghu, 1, time + 5, TimeUnit.SECONDS);
        }
    }

    public String convertObjectToJson(Object obj) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            // 处理异常：实际应用中可能需要更复杂的异常处理逻辑
            log.error("Error converting object to JSON", e);
            return null;
        }
    }


}
