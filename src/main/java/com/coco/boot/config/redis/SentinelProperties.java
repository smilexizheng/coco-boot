package com.coco.boot.config.redis;

import lombok.Data;

@Data
public class SentinelProperties {
    /**
     * 哨兵master 名称
     */
    private String master;

    /**
     * 哨兵节点
     */
    private String nodes;

    /**
     * 哨兵配置
     */
    private boolean masterOnlyWrite;

}
