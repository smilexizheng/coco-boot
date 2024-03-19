package com.coco.boot.config.web;

import com.coco.boot.interceptor.ChatInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ChatInterceptor())
                .addPathPatterns("/v1/**"); // 这里可以自定义拦截的路径
    }
}
