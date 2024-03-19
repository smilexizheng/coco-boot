package com.coco.boot.config.redis;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置
 * @author Hua
 * @since 2024-03-18 15:30
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.redisson")
public class RedissonProperties {

    private String file;
}
