package com.example.bio_platform.dto;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiMessageDTO {
    private String role;    // "user", "admin"
    private String content; // 聊天内容
}