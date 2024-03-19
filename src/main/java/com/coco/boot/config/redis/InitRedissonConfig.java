package com.coco.boot.config.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.IOException;
import java.time.Duration;


/**
 * redisson配置
 * 启用spring RedisHttpSession
 * 启用spring Caching
 * <p>
 * 涉及大型数据缓存 请配置redis服务的 maxmemory-policy缓存清除策略
 * allkeys-lru（LRU策略）、allkeys-lfu（LFU策略）、allkeys-random（随机策略）
 *
 * @author wangye
 */
@Configuration
public class InitRedissonConfig {

    @jakarta.annotation.Resource
    private RedissonProperties redissonProperties;

    @jakarta.annotation.Resource
    private ResourceLoader resourceLoader;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient getRedissonClient() throws IOException {
        Resource configFile = resourceLoader.getResource(redissonProperties.getFile());
        Config config = Config.fromYAML(configFile.getInputStream());
        return Redisson.create(config);
    }


    @Bean
    public CacheManager cacheManager(RedissonClient redissonClient) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                // 处理默认的 cacheName::cacheKey 双::连接
                // .computePrefixWith(name->name+":")
                //缓存过期时间
                .entryTtl(Duration.ofDays(1))
                //序列化方式 默认jdk序列化库 json序列号 RedisSerializer.json()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer()));

        return RedisCacheManager.builder(new RedissonConnectionFactory(redissonClient))
                .cacheDefaults(cacheConfig)
                .build();

//        redissonCache不是pro版本少了些东西，不如使用spring 默认的
//        Map<String, CacheConfig> config = new HashMap<String, CacheConfig>();
//
//        // create "testMap" cache with ttl = 24 minutes and maxIdleTime = 12 minutes
//        config.put("sys", new CacheConfig(24*60*1000, 12*60*1000));
//        return new RedissonSpringCacheManager(redissonClient,config);
    }


}
