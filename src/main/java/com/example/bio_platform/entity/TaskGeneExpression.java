package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_gene_expression")
public class TaskGeneExpression {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String geneId;
    private Integer readCount;
    private LocalDateTime createdAt;
}