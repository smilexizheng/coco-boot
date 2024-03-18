package com.coco.boot.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.URLUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.coco.boot.common.R;
import com.coco.boot.config.CoCoConfig;
import com.coco.boot.entity.ServiceStatus;
import com.coco.boot.pojo.Conversation;
import com.coco.boot.service.CoCoPilotService;
import jodd.util.StringUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;

import static com.coco.boot.constant.SysConstant.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@AllArgsConstructor
@Service
@Slf4j
public class CoCoPilotServiceImpl implements CoCoPilotService {
    /**
     * 获取vscode 最新版本URL
     */
    private static final String VS_CODE_API_URL = "https://api.github.com/repos/microsoft/vscode/releases/latest";
    /**
     * 获取vscode Chat 最新版本URL
     */
    private static final String VS_CODE_CHAT_URL = "https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery";
    /**
     * vscode_version
     */
    private static String vscode_version;
    /**
     * copilot_chat_version;
     */
    private static String copilot_chat_version;

    private static HttpHeaders headersApiGithub;

    static {
        headersApiGithub = new HttpHeaders();
        vscode_version = getLatestVSCodeVersion();
        copilot_chat_version = getLatestChatVersion("GitHub", "copilot-chat");
        headersApiGithub.set("Access-Control-Allow-Origin", "*");
        headersApiGithub.set("Host", "api.github.com");
        headersApiGithub.set("Editor-Version", "vscode/" + vscode_version);
        headersApiGithub.set("Editor-Plugin-Version", "copilot-chat/" + copilot_chat_version);
        headersApiGithub.set("User-Agent", "GitHubCopilotChat/" + copilot_chat_version);
        headersApiGithub.set("Accept", "*/*");
        headersApiGithub.set("Accept-Encoding", "gzip, deflate, br");
    }

    private final RestTemplate rest;
    private final RedissonClient redissonClient;
    private final CoCoConfig coCoConfig;

    /**
     * 每三天定时更新headersApiGithub
     * Editor-Version
     * Editor-Plugin-Version
     * User-Agent
     */
    @Scheduled(cron = "0 0 3 1/3 * ?")
    private static void updateLatestVersion() {
        try {
            String latestVersion = getLatestVSCodeVersion();
            String latestChatVersion = getLatestChatVersion("GitHub", "copilot-chat");
            if (latestVersion != null && latestChatVersion != null) {
                vscode_version = latestVersion;
                copilot_chat_version = latestChatVersion;
                headersApiGithub.set("Editor-Version", "vscode/" + vscode_version);
                headersApiGithub.set("Editor-Plugin-Version", "copilot-chat/" + copilot_chat_version);
                headersApiGithub.set("User-Agent", "GitHubCopilotChat/" + copilot_chat_version);
                log.info("===================配置更新说明========================");
                log.info("vscode_version更新为：" + vscode_version);
                log.info("copilot_chat_version更新为：" + copilot_chat_version);
                log.info("======================================================");
            } else {
                log.error("vscode_version，copilot_chat_version更新失败！");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取最新的vscode版本
     * Editor-Version
     */
    public static String getLatestVSCodeVersion() {
        try {
            URL url = new URL(VS_CODE_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String output;
                while ((output = br.readLine()) != null) {
                    response.append(output);
                }
            }
            conn.disconnect();
            JSONObject jsonObject = JSON.parseObject(response.toString());
            return jsonObject.getString("tag_name");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取最新的github chat版本
     * Editor-Plugin-Version
     * User-Agent
     */
    public static String getLatestChatVersion(String publisher, String name) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(VS_CODE_CHAT_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json;api-version=6.1-preview.1");
            conn.setDoOutput(true);
            com.alibaba.fastjson.JSONObject jsonRequest = getJsonObject(publisher, name);
            OutputStream os = conn.getOutputStream();
            os.write(jsonRequest.toString().getBytes());
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            StringBuilder response = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                response.append(output);
            }
            conn.disconnect();
            JSONObject jsonResponse = JSON.parseObject(response.toString());
            return jsonResponse.getJSONArray("results").getJSONObject(0).getJSONArray("extensions").getJSONObject(0).getJSONArray("versions").getJSONObject(0).getString("version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static com.alibaba.fastjson.JSONObject getJsonObject(String publisher, String name) {
        com.alibaba.fastjson.JSONObject jsonRequest = new com.alibaba.fastjson.JSONObject();
        jsonRequest.put("flags", 870);

        com.alibaba.fastjson.JSONArray filtersArray = new com.alibaba.fastjson.JSONArray();

        com.alibaba.fastjson.JSONObject criteriaObject = new com.alibaba.fastjson.JSONObject();
        criteriaObject.put("filterType", 7);
        criteriaObject.put("value", publisher + "." + name);

        com.alibaba.fastjson.JSONArray criteriaArray = new com.alibaba.fastjson.JSONArray();
        criteriaArray.add(criteriaObject);

        com.alibaba.fastjson.JSONObject filterObject = new com.alibaba.fastjson.JSONObject();
        filterObject.put("criteria", criteriaArray);

        filtersArray.add(filterObject);

        jsonRequest.put("filters", filtersArray);
        return jsonRequest;
    }

    @Override
    public R<String> uploadGhu(String data) {
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
        return R.success(StringUtils.hasLength(msg) ? "不可用数据" + msg : "操作完成");
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
        JSONObject userInfo = responseEntity.getBody();
        assert userInfo != null;
        String userId = userInfo.getString("id");


        String userInfoJsonString = JSON.toJSONString(userInfo);
        // 检测用户信息         0级用户直接ban
        int trustLevel = userInfo.getIntValue("trust_level");
        boolean active = userInfo.getBooleanValue("active");
        if (!active || trustLevel < 1) {
            log.warn("{} trust_level is 0 or  is not active ", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Your trust_level is 0 or  is not active ");
        }
        RBucket<String> users = redissonClient.getBucket(LINUX_DO_USER_ID + userId);
        users.set(userInfoJsonString);
        //虚拟本系统用户信息- 通过此获取到linux userId ，继而可以获取 linux的tokens
        String token = IdUtil.simpleUUID();
        RBucket<String> cocoAuth = redissonClient.getBucket(SYS_USER_ID + token);
        cocoAuth.set(userInfoJsonString, Duration.ofHours(coCoConfig.getUserTokenExpire()));
        return new ResponseEntity<>("{\"message\": \"Token Get Success\", \"data\": \"" + token + "\"}", HttpStatus.OK);
    }

    private void setUserRateLimiter(String userId, int trustLevel) {
        RRateLimiter rateLimiter = this.redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
        RateIntervalUnit timeUnit = RateIntervalUnit.SECONDS;
        rateLimiter.trySetRate(RateType.OVERALL, ((long) coCoConfig.getUserFrequencyDegree() * trustLevel), coCoConfig.getUserRateTime(), timeUnit);
        rateLimiter.expire(Duration.ofMillis(timeUnit.toMillis(coCoConfig.getFrequencyDegree())));
    }

    @Override
    public ResponseEntity<String> chat(Conversation requestBody, String auth) {
        if (!auth.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Authorization");
        } else {
            String token = auth.substring("Bearer ".length());
            RBucket<String> bucket = redissonClient.getBucket(SYS_USER_ID + token);

            if (!bucket.isExists()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token does not exist");
            } else {
                bucket.expireAsync(Duration.ofHours(coCoConfig.getUserTokenExpire()));
                JSONObject userInfo = JSON.parseObject(bucket.get());
                String userId = userInfo.getString("id");

//              根据用户信任级别限流
                RRateLimiter rateLimiter = this.redissonClient.getRateLimiter(USER_RATE_LIMITER + userId);
                if (!rateLimiter.isExists()) {
                    setUserRateLimiter(userId, userInfo.getIntValue("trust_level"));
                }
                if (rateLimiter.tryAcquire()) {
                    // 调用 handleProxy 方法并获取响应
                    ResponseEntity<String> response = handleProxy(requestBody);

                    // 用户访问计数
                    RAtomicLong atomicLong = this.redissonClient.getAtomicLong(USING_USER + userId);
                    atomicLong.incrementAndGet();

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
    private ResponseEntity<String> handleProxy(Object requestBody) {
        // 实现 handleProxy 方法逻辑
        RSet<String> ghuAliveKey = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);

        if (!ghuAliveKey.isExists()) {
            return ResponseEntity.ok("{\"message\": \"No keys\"}");
        }

        String ghu = getGhu(ghuAliveKey);
        if (StringUtil.isBlank(ghu)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("{\"message\": \"Rate limit,The server is under great pressure\"}");
        }
        log.info("{}可用令牌数量，当前选择{}", ghuAliveKey.size(), ghu);


        //TODO 放置到下面接口成功之后   ghu 用量统计
        RAtomicLong atomicLong = this.redissonClient.getAtomicLong(USING_GHU + ghu);
        atomicLong.incrementAndGet();
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
     * 递归改成循环调用。防止栈溢出
     */
    public String getGhu(RSet<String> ghuAliveKey) {
        RRateLimiter rateLimiter;
        int retryCount = 0;
        while (retryCount <= 10) {
            String ghu = ghuAliveKey.random();
            rateLimiter = this.redissonClient.getRateLimiter(GHU_RATE_LIMITER + ghu);
            if (!rateLimiter.isExists()) {
                RateIntervalUnit timeUnit = RateIntervalUnit.SECONDS;
                rateLimiter.trySetRate(RateType.OVERALL, coCoConfig.getFrequencyDegree(), coCoConfig.getFrequencyTime(), timeUnit);
                rateLimiter.expireAsync(Duration.ofMillis(timeUnit.toMillis(coCoConfig.getFrequencyDegree())));
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
