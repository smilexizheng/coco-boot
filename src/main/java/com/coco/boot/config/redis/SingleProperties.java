package com.coco.boot.config.redis;

import lombok.Data;
import org.redisson.config.SentinelServersConfig;

@Data
public class SingleProperties extends SentinelServersConfig {
    private  String address;
}
