package com.example.bio_platform.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

// 🌟 新增的请求接收类
@Data
public class AiChatRequest {
    private String courseContext;
    private List<Map<String, String>> historyMessages;
}
