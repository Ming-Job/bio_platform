package com.example.bio_platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户增长趋势DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGrowthDTO {
    /**
     * 日期（格式：yyyy-MM-dd）
     */
    private String date;

    /**
     * 新增用户数
     */
    private Integer newUsers;

    /**
     * 累计用户数
     */
    private Integer totalUsers;

    /**
     * 增长率（百分比）
     */
    private Double growthRate;
}