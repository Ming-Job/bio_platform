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
@TableName("bio_files")
@ApiModel(value = "FileEntity对象", description = "文件表")
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "文件ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "原始文件名", required = true, example = "sample.fastq.gz")
    @TableField("original_name")
    private String originalName;

    @ApiModelProperty(value = "存储文件名(UUID)", required = true, example = "f47ac10b-58cc-4372-a567-0e02b2c3d479.fastq.gz")
    @TableField("stored_name")
    private String storedName;

    @ApiModelProperty(value = "文件类型: fastq, fasta, bam等", required = true, example = "fastq")
    @TableField("file_type")
    private String fileType;

    @ApiModelProperty(value = "文件扩展名", required = true, example = ".fastq.gz")
    @TableField("file_ext")
    private String fileExt;

    @ApiModelProperty(value = "文件大小(字节)", required = true, example = "1073741824")
    @TableField("size_bytes")
    private Long sizeBytes;

    @ApiModelProperty(value = "相对存储路径", required = true, example = "user001/2024/01/01/f47ac10b-58cc-4372-a567-0e02b2c3d479.fastq.gz")
    @TableField("storage_path")
    private String storagePath;

    @ApiModelProperty(value = "MD5哈希值", required = true, example = "e4d909c290d0fb1ca068ffaddf22cbd0")
    @TableField("md5_hash")
    private String md5Hash;

    @ApiModelProperty(value = "上传用户ID", required = true, example = "1")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "所属项目ID", example = "1")
    @TableField("project_id")
    private Long projectId;

    @ApiModelProperty(value = "文件状态: uploading/uploaded/processing/ready/archived/deleted/error", example = "uploading")
    @TableField("status")
    private String status = "uploading";

    @ApiModelProperty(value = "上传时间")
    @TableField(value = "upload_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime uploadTime;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "文件描述", example = "肝癌样本测序数据")
    @TableField("description")
    private String description;

    // ===================== 以下是计算字段，不映射到数据库 =====================

    @ApiModelProperty(value = "格式化后的文件大小", example = "1.0 GB")
    @TableField(exist = false)
    private String formattedSize;

    @ApiModelProperty(value = "文件下载URL")
    @TableField(exist = false)
    private String downloadUrl;

    @ApiModelProperty(value = "文件预览URL")
    @TableField(exist = false)
    private String previewUrl;

    @ApiModelProperty(value = "用户信息")
    @TableField(exist = false)
    private User user;

    @ApiModelProperty(value = "项目信息")
    @TableField(exist = false)
    private Project project;

    // ===================== 以下是为兼容现有代码保留的字段（可选） =====================
    // 如果需要在业务逻辑中使用这些字段，但不在数据库中存储，可以保留为计算字段

    @ApiModelProperty(value = "上传用户名", example = "张三")
    @TableField(exist = false)
    private String userName;

    @ApiModelProperty(value = "项目名称", example = "肝癌转录组分析")
    @TableField(exist = false)
    private String projectName;

    @ApiModelProperty(value = "是否公开", example = "false")
    @TableField(exist = false)
    private Boolean isPublic = false;

    @ApiModelProperty(value = "访问级别: private/team/public", example = "private")
    @TableField(exist = false)
    private String accessLevel = "private";
}