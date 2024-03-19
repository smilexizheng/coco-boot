package com.coco.boot.constant;

/**
 * 缓存的key
 *
 * @author wangye
 */
public interface SysConstant {

    /**
     * GHu的缓存key
     */
    String GHU_ALIVE_KEY ="ghu_key:alive:";
    String GHU_NO_ALIVE_KEY ="ghu_key:noAlive:";
    String GHU_COOLING_KEY ="ghu_key:cooling:";

    String GHU_RATE_LIMITER = "ghu-rate-limiter:";
    String USER_RATE_LIMITER = "user-rate-limiter:";
    String TOKEN_STATE ="state:";


    String SYS_USER_ID = "sys:user:";
    String LINUX_DO_USER_ID = "linuxdo:user:";


    String LINUX_DO_ACCESS_TOKEN="linuxdo:token:access-token:";
    String LINUX_DO_Refresh_TOKEN="linuxdo:token:refresh-token:";

    String USING_USER = "using:user:";
    String USING_GHU = "using:ghu:";

    String HEADER_RETRY = "x-ratelimit-user-retry-after";


}
