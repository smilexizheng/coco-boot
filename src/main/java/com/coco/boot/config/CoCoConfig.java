package com.coco.boot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CoCo 系统配置
 *
 * @author wangye
 */
@Component
@ConfigurationProperties(prefix = "coco")
public class CoCoConfig {

  
    private String redirectUri;
    private Integer expirationTtl;
    
    private String clientId;
    private String clientSecret;


    private String authorizationEndpoint;


    private String tokenEndpoint;


    private String userEndpoint;


    private Integer frequencyTime;


    private Integer frequencyDegree;
    private Integer userRateTime;
    private Integer userFrequencyDegree;


    public Integer getExpirationTtl() {
        return expirationTtl;
    }

    public void setExpirationTtl(Integer expirationTtl) {
        this.expirationTtl = expirationTtl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserEndpoint() {
        return userEndpoint;
    }

    public void setUserEndpoint(String userEndpoint) {
        this.userEndpoint = userEndpoint;
    }

    public Integer getFrequencyTime() {
        return frequencyTime;
    }

    public void setFrequencyTime(Integer frequencyTime) {
        this.frequencyTime = frequencyTime;
    }

    public Integer getFrequencyDegree() {
        return frequencyDegree;
    }

    public void setFrequencyDegree(Integer frequencyDegree) {
        this.frequencyDegree = frequencyDegree;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public Integer getUserRateTime() {
        return userRateTime;
    }

    public void setUserRateTime(Integer userRateTime) {
        this.userRateTime = userRateTime;
    }

    public Integer getUserFrequencyDegree() {
        return userFrequencyDegree;
    }

    public void setUserFrequencyDegree(Integer userFrequencyDegree) {
        this.userFrequencyDegree = userFrequencyDegree;
    }
}
