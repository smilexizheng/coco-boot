package com.coco.boot.config.redis;

import lombok.Data;

@Data
public class ClusterProperties {
    /**
     * 集群状态扫描间隔时间，单位是毫秒
     */
    private int scanInterval;

    /**
     * 集群节点
     */
    private String nodes;


    /**
     * （从节点连接池大小） 默认值：64
     */
    private int slaveConnectionPoolSize;
    /**
     * 主节点连接池大小）默认值：64
     */
    private int masterConnectionPoolSize;

    /**
     * （命令失败重试次数） 默认值：3
     */
    private int retryAttempts;

    /**
     *命令重试发送时间间隔，单位：毫秒 默认值：1500
     */
    private int retryInterval;

}
