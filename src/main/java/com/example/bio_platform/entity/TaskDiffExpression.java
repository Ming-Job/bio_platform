package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("task_diff_expression")
public class TaskDiffExpression {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long controlTaskId;
    private Long treatTaskId;
    private String geneId;
    private Integer controlCount;
    private Integer treatCount;
    private Double log2FoldChange;
    private Double pValue;
    private LocalDateTime createTime;
}