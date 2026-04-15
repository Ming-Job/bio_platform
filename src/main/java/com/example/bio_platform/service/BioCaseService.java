package com.example.bio_platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.bio_platform.entity.BioCase;

/**
 * 生信案例服务层接口
 */
public interface BioCaseService extends IService<BioCase> {

    /**
     * 管理员：分页查询案例矩阵
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param searchKey 搜索关键词(匹配标题)
     * @param category 算子类别
     * @return 分页结果
     */
    Page<BioCase> getAdminCasePage(Integer pageNum, Integer pageSize, String searchKey, String category);
}