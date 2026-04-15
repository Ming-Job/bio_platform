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

/**
 * 案例矩阵实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("bio_case")
@ApiModel(value = "BioCase对象", description = "案例矩阵表")
public class BioCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "案例ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "案例标题", required = true, example = "16S 扩增子多样性分析管线")
    @TableField("title")
    private String title;

    @ApiModelProperty(value = "算子类别：pipeline, structure, template, copilot", required = true, example = "pipeline")
    @TableField("category")
    private String category;

    @ApiModelProperty(value = "案例标签(逗号分隔)", example = "生信,Docker,16S")
    @TableField("tags")
    private String tags;

    @ApiModelProperty(value = "案例难度：easy, medium, hard", example = "medium")
    @TableField("difficulty")
    private String difficulty;

    @ApiModelProperty(value = "关联的数据集名称(挂载文件名)", example = "sample_data.tar.gz")
    @TableField("dataset")
    private String dataset;

    @ApiModelProperty(value = "预设极客提示词(System Prompt)", example = "你是一个生信专家...")
    @TableField("prompt")
    private String prompt;

    @ApiModelProperty(value = "案例简介")
    @TableField("description")
    private String description;

    @ApiModelProperty(value = "解析文档内容(Markdown格式)")
    @TableField("content")
    private String content;

    @ApiModelProperty(value = "案例封面图片路径", example = "/uploads/sections/pic1.webp")
    @TableField("image_url")
    private String imageUrl;

    // 🌟 核心补充：这里对齐了刚才数据库新加的示例结果图字段
    @ApiModelProperty(value = "预期产出/示例结果图URL", example = "/uploads/results/volcano_plot.png")
    @TableField("result_image_url")
    private String resultImageUrl;

    // 🌟 新增：把数据库刚加的字段映射到 Java 里
    @ApiModelProperty("预期产出结果图的专业解读")
    private String resultImageDesc;

    @ApiModelProperty(value = "使用次数", example = "128")
    @TableField("forks")
    private Integer forks = 0;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    // ===================== 计算字段 (不映射数据库) =====================

    @ApiModelProperty(value = "解析后的标签列表")
    @TableField(exist = false)
    private String[] tagList;
}