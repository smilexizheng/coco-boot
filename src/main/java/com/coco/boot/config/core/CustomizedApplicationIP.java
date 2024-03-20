package com.coco.boot.config.core;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.spi.PropertyDefiner;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 自定义logbackIp获取.
 *
 * @author Hua
 * @since 2024-03-20 10:20
 */
@Slf4j
public class CustomizedApplicationIP extends ClassicConverter implements PropertyDefiner {
    private static String ip;

    static {
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("自定义logbackIp获取异常", e);
        }
    }

    @Override
    public String convert(ILoggingEvent event) {
        return ip;
    }

    /**
     * 用于更新使用IP
     */
    static void updateIp(String eurekaIp) {
        ip = eurekaIp;
    }

    @Override
    public String getPropertyValue() {
        return ip;
    }
}
