package com.coco.boot.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.coco.boot.constant.RiskContrConstant.*;

@Component
@Slf4j
public class RiskContrTask {
    @Resource
    private RedissonClient redissonClient;

    /**
     *  每天重置账号
     */
    @Scheduled(cron = "0 0 2 * * ?")
    private void resetSomeThins() {
        RKeys keys = redissonClient.getKeys();
        //token 次数
        keys.deleteByPattern(RC_GET_TOKEN_NUM + "*");
        //token success次数
        keys.deleteByPattern(RC_TOKEN_SUCCESS_REQ + "*");
        //user success次数
        keys.deleteByPattern(RC_USER_SUCCESS_REQ + "*");
    }

}
