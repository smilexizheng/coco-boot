package com.coco.boot.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "risk-contr")
public class RiskContrConfig {
    private Integer getTokenNum;
    private Integer tokenMaxReq;
    private Integer userMaxReq;
    private Integer userMaxTime;
    private Integer tokenInvalidNum;
    private Integer rejectTimeNum;
    private Integer rejectTime;
    private Integer banNum;
}
