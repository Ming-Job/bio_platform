package com.example.bio_platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.bio_platform.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    /**
     * 根据文件ID查找元数据
     */
    @Select("SELECT * FROM bio_file_metadata WHERE file_id = #{fileId}")
    FileMetadata selectByFileId(@Param("fileId") Long fileId);
}