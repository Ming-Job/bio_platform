package com.example.bio_platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("analysis_pipeline")
@ApiModel(value = "AnalysisPipeline对象", description = "分析流程模板表")
public class AnalysisPipeline implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "模板ID", example = "1")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "流程唯一编码", required = true, example = "rna_seq")
    @TableField("pipeline_code")
    private String pipelineCode;

    @ApiModelProperty(value = "流程名称", required = true, example = "RNA-Seq 差异表达分析")
    @TableField("name")
    private String name;

    @ApiModelProperty(value = "流程描述")
    @TableField("description")
    private String description;

    @ApiModelProperty(value = "所属领域", example = "genomics")
    @TableField("category")
    private String category;

    // 🌟🌟🌟 新增字段：用于绑定底层的静态参考数据
    @ApiModelProperty(value = "绑定的参考基因组文件ID(.fa)", example = "101")
    @TableField("ref_fa_file_id")
    private Long refFaFileId;

    @ApiModelProperty(value = "绑定的注释文件ID(.gtf)", example = "102")
    @TableField("ref_gtf_file_id")
    private Long refGtfFileId;

    // 🌟🌟🌟 核心修改：废弃单文件分类器，改为 VSEARCH 双文件绑定
    @ApiModelProperty(value = "绑定的参考序列库文件ID(含seq)", example = "103")
    @TableField("ref_seqs_file_id")
    private Long refSeqsFileId;

    @ApiModelProperty(value = "绑定的参考物种层级库文件ID(含tax)", example = "104")
    @TableField("ref_tax_file_id")
    private Long refTaxFileId;

    @ApiModelProperty(value = "默认参数(JSON)")
    @TableField("default_params")
    private String defaultParams;

    @ApiModelProperty(value = "展示排序", example = "0")
    @TableField("sort_order")
    private Integer sortOrder;

    @ApiModelProperty(value = "是否上线：0-下线，1-上线", example = "1")
    @TableField("is_active")
    private Integer isActive;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}