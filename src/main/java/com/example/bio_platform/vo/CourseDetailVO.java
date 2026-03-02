package com.example.bio_platform.vo;

import com.example.bio_platform.dto.CourseChapterDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "CourseDetailVO对象", description = "课程详情（含大纲）")
public class CourseDetailVO extends CourseVO {

    @ApiModelProperty(value = "课程大纲（包含章节与课时列表）")
    private List<CourseChapterDTO> chapters;

}