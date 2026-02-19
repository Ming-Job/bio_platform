package com.example.bio_platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "bio.file")  // 注意这里的前缀
public class FileUploadProperties {

    /**
     * 文件存储基础目录
     */
    private String baseDir = "D:/bio_uploads/files";

    /**
     * 分片上传临时目录
     */
    private String chunkTempDir = "D:/bio_uploads/temp";

    /**
     * 基础访问URL
     */
    private String baseUrl = "http://localhost:8080/api/files";

    /**
     * 允许的文件扩展名列表
     */
    private List<String> allowedExtensions = List.of(
            ".fastq", ".fastq.gz", ".fq", ".fq.gz",
            ".fasta", ".fa", ".fa.gz", ".fasta.gz", ".bam", ".sam"
    );

    /**
     * 最大文件大小（字节）
     */
    private Long maxFileSize = 10L * 1024 * 1024 * 1024; // 10GB
}