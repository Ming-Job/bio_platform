package com.example.bio_platform.dto;

import lombok.Data;
import java.util.List;

@Data
public class TaskSubmitDTO {

    // 🌟 核心新增：接收前端传来的项目 ID
    private Long projectId;

    // 流程模板 ID
    private Long pipelineId;

    // 挂载的文件 ID 列表
    private List<Long> fileIds;

    // 接收前端 textarea 发来的字符串
    private String params;
}