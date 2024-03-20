package com.coco.boot.config.redis;


import lombok.Data;

@Data
public class PoolProperties {

    private int minIdle;

    private int connTimeout;

    private int soTimeout;

    /**
     * 池大小
     */
    private  int size;



}
