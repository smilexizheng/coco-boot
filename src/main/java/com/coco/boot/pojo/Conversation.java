package com.coco.boot.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author 用于规范发送给github copilot chat的消息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Conversation {

    /**
     * 模型名
     */
    private String model;

    /**
     * 发送给chat接口的消息
     */
    private List<Map<String, String>> messages;

    /**
     * 是否流式
     */
    private Boolean stream;

}
