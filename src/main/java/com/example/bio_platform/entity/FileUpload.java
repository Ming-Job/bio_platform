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
@TableName("bio_file_uploads")
@ApiModel(value = "FileUpload对象", description = "文件上传记录表")
public class FileUpload implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "上传记录ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "关联的文件ID", example = "1")
    @TableField("file_id")
    private Long fileId;

    @ApiModelProperty(value = "关联的用户ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @ApiModelProperty(value = "上传会话ID", required = true, example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    @TableField("upload_session")
    private String uploadSession;

    @ApiModelProperty(value = "上传方式: direct/chunked/ftp/sync", example = "direct")
    @TableField("upload_method")
    private String uploadMethod = "direct";

    @ApiModelProperty(value = "是否分片上传", example = "false")
    @TableField("chunked")
    private Boolean chunked = false;

    @ApiModelProperty(value = "总分片数", example = "0")
    @TableField("total_chunks")
    private Integer totalChunks = 0;

    @ApiModelProperty(value = "已上传分片数", example = "0")
    @TableField("uploaded_chunks")
    private Integer uploadedChunks = 0;

    @ApiModelProperty(value = "已上传字节数", example = "0")
    @TableField("bytes_uploaded")
    private Long bytesUploaded = 0L;

    @ApiModelProperty(value = "总字节数", required = true, example = "1073741824")
    @TableField("bytes_total")
    private Long bytesTotal;

    @ApiModelProperty(value = "上传状态: initializing/uploading/verifying/completed/failed/cancelled/timeout", required = true, example = "initializing")
    @TableField("status")
    private String status = "initializing";

    @ApiModelProperty(value = "开始时间")
    @TableField(value = "start_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "结束时间")
    @TableField("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    // 以下为不映射到数据库的字段

    /**
     * 是否为重复文件（仅用于前端展示，不存入数据库）
     */
    @TableField(exist = false) // MyBatis-Plus 用法
    private Boolean isDuplicate;

    /**
     * 前端提示信息（仅用于前端展示，不存入数据库）
     */
    @TableField(exist = false)
    private String message;

    @ApiModelProperty(value = "文件信息")
    @TableField(exist = false)
    private File file;

    @ApiModelProperty(value = "用户信息")
    @TableField(exist = false)
    private User user;

    @ApiModelProperty(value = "格式化后的已上传大小", example = "500 MB")
    @TableField(exist = false)
    private String formattedBytesUploaded;

    @ApiModelProperty(value = "格式化后的总大小", example = "1.0 GB")
    @TableField(exist = false)
    private String formattedBytesTotal;

    @ApiModelProperty(value = "剩余时间估算", example = "5分钟")
    @TableField(exist = false)
    private String estimatedRemainingTime;
}