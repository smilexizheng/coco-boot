package com.coco.boot.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.coco.boot.common.R;
import com.coco.boot.config.CoCoConfig;
import com.coco.boot.entity.ServiceStatus;
import com.coco.boot.interceptor.ChatInterceptor;
import com.coco.boot.pojo.Conversation;
import com.coco.boot.service.CoCoPilotService;
import jodd.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.coco.boot.constant.SysConstant.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@AllArgsConstructor
@Service
@Slf4j
public class CoCoPilotServiceImpl implements CoCoPilotService {


    private final RestTemplate rest;

    private final RedissonClient redissonClient;

    private final CoCoConfig coCoConfig;

//    private static final HttpHeaders apiHeaders;
//
//    static {
//        apiHeaders = new HttpHeaders();
//        apiHeaders.set("Access-Control-Allow-Origin", "*");
//        apiHeaders.set("Host", "api.cocopilot.com");
//        apiHeaders.set("Editor-Version", "vscode/1.85.2");
//        apiHeaders.set("Editor-Plugin-Version", "copilot-chat/0.11.1");
//        apiHeaders.set("User-Agent", "GitHubCopilotChat/0.11.1");
//        apiHeaders.set("Accept", "*/*");
//        apiHeaders.set("Accept-Encoding", "gzip, deflate, br");
//    }

    @Override
    public R<String> uploadGhu(String data) {
        if (!StringUtils.hasLength(data)) {
            return R.fail();
        }

        //不符合的数据记录
        Map<String, String> map = new HashMap<>();
        // 处理数据换行
        String[] ghuArray = data.split("%0A|\\r?\\n");
        RSet<String> ghus = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        for (String ghu : ghuArray) {
            if (!ghu.startsWith("gh")) {
                map.put(ghu, "格式错误");
                continue;
            }

            if (ghus.contains(ghu)) {
                map.put(ghu, "重复添加");
                log.info("重复添加GHU:{}", ghu);
                continue;
            }
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("Authorization", "token " + ghu);
            //存活校验
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(httpHeaders);

            ResponseEntity<JSONObject> response = rest.exchange(coCoConfig.getBaseApi(), HttpMethod.GET, requestEntity, JSONObject.class);

            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED || response.getStatusCode() == HttpStatus.FORBIDDEN) {
                map.put(ghu, "失效");
                log.warn("upload 存活校验失效: {}", ghu);
            } else if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                Integer retry = Integer.valueOf(response.getHeaders().get(HEADER_RETRY).get(0));
                log.info("upload 存活校验限流: {}, 返回: {}", ghu, response.getBody());
            } else {
                map.put(ghu, "存活");
                ghus.add(ghu);
            }
        }
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
    public ResponseEntity<String> chat(Conversation requestBody, String auth, String path) {
        JSONObject userInfo = ChatInterceptor.tl.get();
        String userId = userInfo.getString("id");
        // 根据用户信任级别限流
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
        if (!rateLimiter.isExists()) {
            RateIntervalUnit timeUnit = RateIntervalUnit.MINUTES;
            rateLimiter.trySetRate(RateType.OVERALL, ((long) coCoConfig.getUserFrequencyDegree() * userInfo.getIntValue("trust_level")), coCoConfig.getUserRateTime(), timeUnit);
            rateLimiter.expireAsync(Duration.ofMinutes(30));
        }

        if (rateLimiter.tryAcquire()) {
            // 调用 handleProxy 方法并获取响应
            ResponseEntity<String> response = handleProxy(requestBody, path);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } else {
            log.warn("用户ID:{}，trustLevel:{}，token:{}被限流使用", userId, userInfo.getIntValue("trust_level"), auth.substring("Bearer ".length()));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Your Rate limit");
        }

    }

    @Override
    public ServiceStatus getServiceStatus() {
        ServiceStatus status = new ServiceStatus();
        int aliveCount = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE).size();
        int noAliveCount = redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE).size();
        status.setGhuAliveCount(aliveCount);
        status.setGhuNoAliveCount(noAliveCount);
        status.setGhuCount(aliveCount + noAliveCount);
        return status;
    }


    /**
     * 核心代理 方法
     */
    private ResponseEntity<String> handleProxy(Object requestBody, String path) {
        // 实现 handleProxy 方法逻辑
        // 进来后再判断一次，拦截器判断通过，但万一其他线程正好用完了
        RSet<String> ghuAliveKey = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        if (!ghuAliveKey.isExists()) {
            return ResponseEntity.ok("{\"message\": \"No keys\"}");
        }
        return getBaseProxyResponse(requestBody, path, ghuAliveKey);
    }

    @NotNull
    private ResponseEntity<String> getBaseProxyResponse(Object requestBody, String path, RSet<String> ghuAliveKey) {
        int i = 0;
        while (i < 2) {
            String ghu = getGhu(ghuAliveKey);
            if (StringUtil.isBlank(ghu)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Rate limit,The server is under great pressure\"}");
            }
            log.info("{}可用令牌数量，当前选择{}", ghuAliveKey.size(), ghu);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + ghu);
            StopWatch sw = new StopWatch();
            sw.start("进入代理");
            ResponseEntity<String> response = rest.postForEntity(coCoConfig.getBaseProxy() + path, new HttpEntity<>(requestBody, headers), String.class);
            sw.stop();
            log.info(sw.prettyPrint(TimeUnit.SECONDS));
            if (response.getStatusCode().is2xxSuccessful()) {
                //ghu使用成功次数
                RAtomicLong atomicLong = redissonClient.getAtomicLong(USING_GHU + ghu);
                atomicLong.incrementAndGet();
                return response;
            } else {
                // 异步移除，并添加到不可用key
                ghuAliveKey.remove(ghu);
                redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE).addAsync(ghu);
                i++;
            }
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Too Many Requests\"}");
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
                rateLimiter.expireAsync(Duration.ofHours(2));
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
}
