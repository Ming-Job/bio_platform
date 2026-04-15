package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("task_gwas_result")
public class TaskGwasResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String chr;
    private String snp;
    private Long bp;
    private Double pValue;
    private String refAllele;  // 参考等位基因 (对应 allele0, 第6列)
    private String altAllele;  // 突变等位基因 (对应 allele1, 第5列)
    private Double maf;        // 等位基因频率 (对应 af, 第7列)
    private Double beta;       // 效应大小 (对应 beta, 第8列)
    private LocalDateTime createdAt;



}