package com.atguigu.process.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.model.process.ProcessTemplate;

/**
 * <p>
 * 审批模板 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-03-08
 */
public interface OaProcessTemplateService extends IService<ProcessTemplate> {

    IPage<ProcessTemplate> selectPageProcessTempate(Page<ProcessTemplate> pageParam);

    void publish(Long id);
}
