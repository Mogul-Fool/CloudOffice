package com.atguigu.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wwj.model.system.SysRole;
import com.wwj.vo.system.AssginRoleVo;

import java.util.Map;

public interface SysRoleService extends IService<SysRole> {

    Map<String, Object> findRoleDataByUserId(Long userId);

    void doAssign(AssginRoleVo assginRoleVo);
}
