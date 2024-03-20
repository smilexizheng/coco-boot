package com.coco.boot.task;

import com.coco.boot.config.CoCoConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RKeys;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static com.coco.boot.constant.RiskContrConstant.*;
import static com.coco.boot.constant.SysConstant.*;

@Component
@Slf4j
public class CoCoTask {
    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RestTemplate rest;

    @Resource
    private CoCoConfig coCoConfig;

    /**
     * 重置账号
     * 凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
//    @Scheduled(cron = "0/10 * * * * ?")启动后，test()添加数据， 此定时任务 10秒后执行
    private void resetSomeThins() {
        RKeys keys = redissonClient.getKeys();
        //token 次数
        keys.deleteByPattern(RC_GET_TOKEN_NUM + "*");
        //token success次数
        keys.deleteByPattern(RC_TOKEN_SUCCESS_REQ + "*");
        //user success次数
        keys.deleteByPattern(RC_USER_SUCCESS_REQ + "*");
    }


    /**
     * 检查一下key吧
     * 凌晨1点执行
     */
    @Scheduled(cron = "0 0 1 * * ?")
//    @Scheduled(cron = "0/10 * * * * ?") //启动后，test()添加数据， 此定时任务 10秒后执行
    private void checkSomeKey() {
        RSet<String> noAlive = redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE);
        RSet<String> alive = redissonClient.getSet(GHU_ALIVE_KEY, StringCodec.INSTANCE);
        RAtomicLong noAliveNum = redissonClient.getAtomicLong(COUNT_NO_ALIVE_KEY);

        for (String key : noAlive.readAll()) {
            if (alive.contains(key)) {
                continue;
            }
            RAtomicLong checkNum = redissonClient.getAtomicLong(CHECK_NO_ALIVE_KEY + DigestUtils.md5DigestAsHex(key.getBytes()));
            long num = checkNum.incrementAndGet();

            try {
                CompletableFuture<HttpResponse<String>> future = AliveTest(key, coCoConfig);
                future.thenAccept(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode == HttpStatus.OK.value() || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
                        log.info(" get one alive  key:{}", key);
                        alive.add(key);
                        checkNum.deleteAsync();
                        noAlive.removeAsync(key);
                    } else {
                        log.info("no alive key:{}", key);
                        if (num > 10) {
                            checkNum.deleteAsync();
                            noAlive.removeAsync(key);
                            noAliveNum.incrementAndGet();
                        }
                    }
                });


            } catch (Exception e) {
                log.error("check 校验异常", e);
            }


        }

    }

    public static CompletableFuture<HttpResponse<String>> AliveTest(String key, CoCoConfig coCoConfig) {
        HttpClient httpClient = HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(coCoConfig.getBaseApi()))
                .headers("Content-Type", "application/json", "Authorization", "token " + key)
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 添加测试数据
     */
//    @PostConstruct  //开启注解测试
    private void test() {
        //  TODO 测试通过后 删除此代码
        RSet<String> noAlive = redissonClient.getSet(GHU_NO_ALIVE_KEY, StringCodec.INSTANCE);
        noAlive.add("test7897878978");
        noAlive.add("test78123");
        noAlive.add("test7887415151");
        String tokenKey = "testsdfsdfsdf";
        redissonClient.getAtomicLong(RC_TOKEN_SUCCESS_REQ + tokenKey).incrementAndGet();
        redissonClient.getAtomicLong(RC_GET_TOKEN_NUM + tokenKey).incrementAndGet();
        redissonClient.getAtomicLong(RC_USER_SUCCESS_REQ + tokenKey).incrementAndGet();
    }

}
