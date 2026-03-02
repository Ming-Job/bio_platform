package com.example.bio_platform.dto;

import com.example.bio_platform.vo.CourseLessonVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ApiModel(description = "课程大纲章节对象")
public class CourseChapterDTO {
    @ApiModelProperty(value = "章节名称")
    private String chapterName;

    @ApiModelProperty(value = "该章节下的课时列表")
    private List<CourseLessonVO> lessons;
}