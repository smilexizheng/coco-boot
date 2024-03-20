package com.coco.boot.config.core;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.coco.boot.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 自定义logbackIp获取.
 * @author Hua
 * @since 2024-03-20 10:20
 */
public class CustomizedRemoteIp extends ClassicConverter{
    @Override
    public String convert(ILoggingEvent event) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return "127.0.0.1";
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return IpUtils.getIpAddr(request);
    }
}
