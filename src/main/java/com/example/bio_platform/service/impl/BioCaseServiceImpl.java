package com.example.bio_platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bio_platform.entity.BioCase;
import com.example.bio_platform.mapper.BioCaseMapper;
import com.example.bio_platform.service.BioCaseService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 生信案例服务层实现类
 */
@Service
public class BioCaseServiceImpl extends ServiceImpl<BioCaseMapper, BioCase> implements BioCaseService {

    @Override
    public Page<BioCase> getAdminCasePage(Integer pageNum, Integer pageSize, String searchKey, String category) {
        Page<BioCase> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BioCase> wrapper = new LambdaQueryWrapper<>();

        // 模糊搜索：案例名称
        if (StringUtils.hasText(searchKey)) {
            wrapper.like(BioCase::getTitle, searchKey);
        }

        // 精确匹配：算子类别 (pipeline / structure / template / copilot)
        if (StringUtils.hasText(category)) {
            wrapper.eq(BioCase::getCategory, category);
        }

        // 按照创建时间倒序排列，最新部署的案例排在最前
        wrapper.orderByDesc(BioCase::getCreateTime);

        return this.page(pageParam, wrapper);
    }
}