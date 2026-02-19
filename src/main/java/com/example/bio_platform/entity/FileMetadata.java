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
@TableName("bio_file_metadata")
@ApiModel(value = "FileMetadata对象", description = "文件元数据表")
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "元数据ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "关联的文件ID", required = true, example = "1")
    @TableField("file_id")
    private Long fileId;

    @ApiModelProperty(value = "样本ID", example = "S001")
    @TableField("sample_id")
    private String sampleId;

    @ApiModelProperty(value = "样本名称", example = "肝癌组织样本")
    @TableField("sample_name")
    private String sampleName;

    @ApiModelProperty(value = "生物体", example = "Homo sapiens")
    @TableField("organism")
    private String organism;

    @ApiModelProperty(value = "实验类型", example = "RNA-Seq")
    @TableField("experiment_type")
    private String experimentType;

    @ApiModelProperty(value = "是否双端测序", example = "true")
    @TableField("paired_end")
    private Boolean pairedEnd = false;

    @ApiModelProperty(value = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    // 以下为不映射到数据库的字段

    @ApiModelProperty(value = "文件信息")
    @TableField(exist = false)
    private File file;
}