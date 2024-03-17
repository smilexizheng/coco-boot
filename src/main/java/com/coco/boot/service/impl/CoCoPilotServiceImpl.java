package com.coco.boot.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.coco.boot.common.R;
import com.coco.boot.config.CoCoConfig;
import com.coco.boot.service.CoCoPilotService;
import jodd.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static com.coco.boot.constant.SysConstant.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@AllArgsConstructor
@Service
@Slf4j
public class CoCoPilotServiceImpl implements CoCoPilotService {


    private final RestTemplate rest;

    private final RedissonClient redissonClient;
    private final CoCoConfig coCoConfig;


    private static final HttpHeaders headersApiGithub;

    static {
        headersApiGithub = new HttpHeaders();
        headersApiGithub.set("Access-Control-Allow-Origin", "*");
        headersApiGithub.set("Host", "api.github.com");
        headersApiGithub.set("Editor-Version", "vscode/1.85.2");
        headersApiGithub.set("Editor-Plugin-Version", "copilot-chat/0.11.1");
        headersApiGithub.set("User-Agent", "GitHubCopilotChat/0.11.1");
        headersApiGithub.set("Accept", "*/*");
        headersApiGithub.set("Accept-Encoding", "gzip, deflate, br");
    }

    @Override
    public R<String> updaloadGhu(String data) {
        if (!StringUtils.hasLength(data)) {
            return R.fail();
        }

        //不符合的数据记录
        StringBuilder sb = new StringBuilder();
        // 处理数据换行
        String[] ghuArray = data.split("%0A|\\r?\\n");
        RSet<String> ghus = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        for (String ghu : ghuArray) {
            if (!ghu.startsWith("gh")) {
                sb.append(ghu);
                continue;
            }
            System.out.println(ghu);

            headersApiGithub.set("authorization", "token " + ghu);

//存活校验
//            ResponseEntity<String> response = rest.postForEntity("https://api.github.com/copilot_internal/v2/token", new HttpEntity<>(null, headersApiGithub), String.class);
//            if (response.getStatusCode() == HttpStatus.OK) {
//                JSONObject result = JSON.parseObject(response.getBody());
//                String token = result.getString("token");
            ghus.add(ghu);
//
//            }
        }


        String msg = sb.toString();
        return R.success(StringUtils.hasLength(msg) ? "不可数据" + msg : "操作完成");
    }


    @Override
    public ModelAndView token() {
        String encodedRedirectUri = URLUtil.encode(coCoConfig.getRedirectUri());
        String stateKey = IdUtil.simpleUUID();
        // 存储 state 值到 KV 中，以便稍后验证
        // 这里可以使用 Redis、Memcached 或者其他存储方式
        // 存活时间设置为5分钟
        RBucket<Integer> state = redissonClient.getBucket(TOKEN_STATE + stateKey);
        state.set(1, Duration.ofMinutes(5));
        String authUrl = coCoConfig.getAuthorizationEndpoint() + "?client_id=CLIENT_ID&state=" + stateKey + "&redirect_uri=" + encodedRedirectUri + "&response_type=code&scope=read";

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
        ResponseEntity<Map> response = rest.postForEntity(coCoConfig.getTokenEndpoint(), new HttpEntity<>(requestBody, headers), Map.class);


        Map<String, Object> tokenData = response.getBody();
        String accessToken = (String) tokenData.get("access_token");
        if (StringUtil.isBlank(accessToken)) {
            return new ResponseEntity<>("Error fetching token", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String expires_in = (String) tokenData.get("expires_in");
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        Duration between = Duration.between(LocalDateTime.now(), LocalDateTime.parse(expires_in, formatter));
        System.out.println("token有效期：" + between);

        // 使用 access_token 获取用户信息
        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);
        ResponseEntity<JSONObject> responseEntity = rest.exchange(coCoConfig.getUserEndpoint(), HttpMethod.GET, new HttpEntity<>(userInfoHeaders), JSONObject.class);
        JSONObject userInfo = responseEntity.getBody();
        assert userInfo != null;
        String userId = userInfo.getString("id");

        RBucket<String> users = redissonClient.getBucket(LINUX_DO_USER_ID + userId);
        String userInfoJsonString = JSON.toJSONString(userInfo);
        // 检测用户信息         0级用户直接ban
        int trustLevel = userInfo.getIntValue("trust_level");
        boolean active = userInfo.getBooleanValue("active");
        if (!active || trustLevel < 1) {
            log.warn("{} trust_level is 0 or  is not active ", userId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Your trust_level is 0 or  is not active ");
        }
        users.set(userInfoJsonString);
        // linux do  token
        RBucket<String> access_tokens = redissonClient.getBucket(LINUX_DO_ACCESS_TOKEN + userId);
        access_tokens.set(accessToken, between);
        RBucket<String> refresh_tokens = redissonClient.getBucket(LINUX_DO_Refresh_TOKEN + userId);
        //refresh_token 增加5分钟
        refresh_tokens.set((String) tokenData.get("refresh_token"), between.plusMinutes(5));

        //虚拟本系统用户信息- 通过此获取到linux userId ，继而可以获取 linux的tokens
        String token = IdUtil.simpleUUID();
        RBucket<String> cocoAuth = redissonClient.getBucket(SYS_USER_ID + token);
        cocoAuth.set(userInfoJsonString);
        // 开始限流信息设置
        setUserRateLimiter(userId, trustLevel);
        return new ResponseEntity<>("{\"message\": \"Token Get Success\", \"data\": \"" + token + "\"}", HttpStatus.OK);
    }

    private void setUserRateLimiter(String userId, int trustLevel) {
        RRateLimiter rateLimiter = this.redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
        RateIntervalUnit timeUnit = RateIntervalUnit.SECONDS;
        rateLimiter.trySetRate(RateType.OVERALL, ((long) coCoConfig.getUserFrequencyDegree() * trustLevel), coCoConfig.getUserRateTime(), timeUnit);
        rateLimiter.expireAsync(Duration.ofMillis(timeUnit.toMillis(coCoConfig.getFrequencyDegree())));
    }

    @Override
    public ResponseEntity<String> chat(Object requestBody, String auth) {
        if (!auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Authorization");
        } else {
            String token = auth.substring("Bearer ".length());
            RBucket<String> bucket = redissonClient.getBucket(SYS_USER_ID + token);

            if (!bucket.isExists()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token does not exist");
            } else {
                JSONObject userInfo = JSON.parseObject(bucket.get());
                String userId = userInfo.getString("id");

//              根据用户信任级别限流
                RRateLimiter rateLimiter = this.redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
                if (!rateLimiter.isExists()){
                    setUserRateLimiter(userId, userInfo.getIntValue("trust_level"));
                }
                if (rateLimiter.tryAcquire()) {
                    // 调用 handleProxy 方法并获取响应
                    ResponseEntity<String> response = handleProxy(requestBody);
//                HttpHeaders newHeaders = new HttpHeaders(response.getHeaders());
//                newHeaders.set("Access-Control-Allow-Origin", "*");
//                newHeaders.set("Access-Control-Allow-Methods", "OPTIONS,POST,GET");
//                newHeaders.set("Access-Control-Allow-Headers", "*");
                    return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
                } else {
                    log.warn("用户ID:{}，trustLevel：{}，token:{}被限流使用", userId, userInfo.getIntValue("trust_level"), token);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Your Rate limit");
                }


            }
        }
    }




    /**
     * 核心代理 方法
     */
    private ResponseEntity<String> handleProxy(Object requestBody) {
        // 实现 handleProxy 方法逻辑
        RSet<String> ghuAliveKey = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        if (!ghuAliveKey.isExists()) {
            return ResponseEntity.ok("{\"message\": \"No keys\"}");
        }

        String ghu = getGhu(ghuAliveKey, 0);
        if (StringUtil.isBlank(ghu)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Rate limit,The server is under great pressure\"}");
        }
        log.info("{}可用令牌数量，当前选择{}", ghuAliveKey.size(), ghu);


//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("Authorization", "Bearer " + ghu);
//        RestTemplate restTemplate = new RestTemplate();
//        ResponseEntity<String> response = restTemplate.postForEntity("https://proxy.cocopilot.org/v1/chat/completions", new HttpEntity<>(requestBody, headers), String.class);
//        // 429被限制
//        if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
//            String retryAfter = response.getHeaders().getFirst("x-ratelimit-user-retry-after");
//            // 保留原有判断
//            if (retryAfter != null) {
//
//            }
////            异步移除，并添加到不可用key
//            ghuAliveKey.removeAsync(ghu);
//            RSet<String> ghuNoAliveKey = redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE);
//
//            ghuNoAliveKey.addAsync(ghu);
//            //重写响应
//            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Rate limit\"}");
//        }

//        return response;

//        临时全部返回成功
        return ResponseEntity.status(HttpStatus.OK).body("{\"message\": \"OK\"}");

    }

    /**
     * 限流随机现有GHU
     */
    public String getGhu(RSet<String> ghuAliveKey, int retryCount) {
        if (retryCount > 10) {
            return null;
        }
        String ghu = ghuAliveKey.random();
        RRateLimiter rateLimiter = this.redissonClient.getRateLimiter(GHU_RATE_LIMITER + ghu);
        RateIntervalUnit timeUnit = RateIntervalUnit.SECONDS;
        rateLimiter.trySetRate(RateType.OVERALL, coCoConfig.getFrequencyDegree(), coCoConfig.getFrequencyTime(), timeUnit);
        rateLimiter.expireAsync(Duration.ofMillis(timeUnit.toMillis(coCoConfig.getFrequencyDegree())));
        if (rateLimiter.tryAcquire()) {
            return ghu;
        } else {
            log.info("{} 被限流使用", ghu);
            return getGhu(ghuAliveKey, ++retryCount);
        }
    }


    /**
     * 通过linux refresh_token 获取用户token
     */
    private boolean refreshToken(String userId, String token) {
        if (StringUtil.isBlank(token)) {
            return false;
        }

        // 构造请求的 body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", token.substring(token.indexOf(',') + 1));
        body.add("client_id", coCoConfig.getClientId());
        body.add("client_secret", coCoConfig.getClientSecret());

        try {
            // 发送 POST 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            ResponseEntity<JSONObject> response = rest.postForEntity(coCoConfig.getTokenEndpoint(), body, JSONObject.class);


            if (response.getStatusCode().is2xxSuccessful()) {
                // 解析响应体
                JSONObject json = response.getBody();
                assert json != null;
                String accessToken = json.getString("access_token");
                String refreshToken = json.getString("refresh_token");


//                TODO 过期时间 转换 二选一
                // 日期时间格式转换
                String expiresIn = json.getString("expires_in");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                LocalDateTime expirTime = LocalDateTime.parse(expiresIn, formatter);
// 时间戳转换
//                long timestamp  =  json.getLong("expires_in");
//                Instant instant = Instant.ofEpochMilli(timestamp);
//                expirTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

                Duration between = Duration.between(LocalDateTime.now(), expirTime);
                System.out.println("token有效期：" + between);

                RBucket<Object> at_b = redissonClient.getBucket(LINUX_DO_ACCESS_TOKEN + userId);
                RBucket<Object> rt_b = redissonClient.getBucket(LINUX_DO_Refresh_TOKEN + userId);
                at_b.set(accessToken, between);
                //refresh_token 增加5分钟
                rt_b.set(refreshToken, between.plusMinutes(5));
                return true;
            }
        } catch (Exception e) {
            log.error("刷新L站Token", e);
            return false;
        }

        return false;


    }
}
