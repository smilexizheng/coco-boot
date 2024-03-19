package com.coco.boot.config.web;

import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;

/**
 * 自定义响应错误处理器
 * @author Hua
 * @since 2024-03-19 09:20
 */
@Component
public class RestErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        // 这里可以根据需要调整判断逻辑
        // 默认情况下，4xx和5xx视为错误，这里我们允许它们通过，不视为错误
        return false;
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        // 由于我们在hasError()中不将任何响应视为错误，所以这里不需要实现任何处理
    }
}
