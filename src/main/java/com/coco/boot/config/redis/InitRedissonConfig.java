package com.coco.boot.config.redis;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.*;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

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

    @Resource
    RedissonProperties redisProperties;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient getRedissonClient() {
        Config config = new Config();
        String mode = redisProperties.getMode();
        switch (mode) {
            case "single" -> {
                SingleServerConfig serverConfig = config.useSingleServer()
                        .setAddress(redisProperties.getSingle().getAddress())
                        .setTimeout(redisProperties.getPool().getConnTimeout())
                        .setConnectionPoolSize(redisProperties.getPool().getSize())
                        .setDatabase(redisProperties.getDatabase())
                        .setConnectionMinimumIdleSize(redisProperties.getPool().getMinIdle());
                if (StringUtils.isNotBlank(redisProperties.getPassword())) {
                    serverConfig.setPassword(redisProperties.getPassword());
                }
            }
            case "cluster" -> {
                String[] nodes = redisProperties.getCluster().getNodes().split(",");
                ClusterServersConfig serverConfig = config.useClusterServers()
                        .addNodeAddress(nodes)
                        .setScanInterval(
                                redisProperties.getCluster().getScanInterval())
                        .setIdleConnectionTimeout(
                                redisProperties.getPool().getSoTimeout())
                        .setConnectTimeout(
                                redisProperties.getPool().getConnTimeout())
                        .setRetryAttempts(
                                redisProperties.getCluster().getRetryAttempts())
                        .setRetryInterval(
                                redisProperties.getCluster().getRetryInterval())
                        .setMasterConnectionPoolSize(redisProperties.getCluster()
                                .getMasterConnectionPoolSize())
                        .setSlaveConnectionPoolSize(redisProperties.getCluster()
                                .getSlaveConnectionPoolSize())
                        .setReadMode(redisProperties.getReadMode())
                        .setSubscriptionMode(redisProperties.getSubscriptionMode())
                        .setTimeout(redisProperties.getTimeout());
                if (StringUtils.isNotBlank(redisProperties.getPassword())) {
                    serverConfig.setPassword(redisProperties.getPassword());
                }
            }
            case "sentinel" -> {
                String[] nodes = redisProperties.getSentinel().getNodes().split(",");
                SentinelServersConfig serverConfig = config.useSentinelServers()
                        .addSentinelAddress(nodes)
                        .setMasterName(redisProperties.getSentinel().getMaster())
                        .setReadMode(redisProperties.getReadMode())
                        .setSubscriptionMode(redisProperties.getSubscriptionMode())
                        .setTimeout(redisProperties.getTimeout())
                        .setDatabase(redisProperties.getDatabase())
                        .setMasterConnectionPoolSize(redisProperties.getPool().getSize())
                        .setSlaveConnectionPoolSize(redisProperties.getPool().getSize());

                if (StringUtils.isNotBlank(redisProperties.getPassword())) {
                    serverConfig.setPassword(redisProperties.getPassword());
                }
            }
        }

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
