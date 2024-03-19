package com.coco.boot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CoCo 系统配置
 *
 * @author wangye
 */
@Data
@Component
@ConfigurationProperties(prefix = "coco")
public class CoCoConfig {
    private String redirectUri;
    private Integer expirationTtl;
    private String clientId;
    private String clientSecret;
    private String authorizationEndpoint;
    private String baseProxy;
    private String baseApi;
    private String tokenEndpoint;
    private String userEndpoint;
    private Integer frequencyTime;
    private Integer frequencyDegree;
    private Integer userRateTime;
    private Integer userFrequencyDegree;
    private Integer userTokenExpire;
    private Integer userLevel;
}


