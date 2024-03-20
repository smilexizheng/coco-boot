package com.coco.boot.config.redis;

import lombok.Data;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置
 *
 * @author Hua
 * @since 2024-03-18 15:30
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "redisson")
public class RedissonProperties {
    private Integer database;

    /**
     * 等待节点回复命令的时间。该时间从命令发送成功时开始计时
     */
    private Integer timeout;

    private String password;

    private String mode;

    /**
     * 默认值： SLAVE（只在从服务节点里读取）设置读取操作选择节点的模式。 可用值为： SLAVE - 只在从服务节点里读取。
     * MASTER - 只在主服务节点里读取。 MASTER_SLAVE - 在主从服务节点里都可以读取
     */
    private ReadMode readMode;
    private SubscriptionMode subscriptionMode;

    /**
     * 池配置
     */
    private PoolProperties pool;

    /**
     * 单机信息配置
     */
    private SingleProperties single;

    /**
     * 集群 信息配置
     */
    private ClusterProperties cluster;

    /**
     * 哨兵配置
     */
    private SentinelProperties sentinel;
}
