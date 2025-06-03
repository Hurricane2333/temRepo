package com.ld.reborn.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.dao.WebInfoMapper;
import com.ld.reborn.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 后台 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private WebInfoMapper webInfoMapper;

    /**
     * 获取网站信息
     */
    @GetMapping("/webInfo/getAdminWebInfo")
    @LoginCheck(0)
    public RebornResult<WebInfo> getWebInfo() {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            return RebornResult.success(list.get(0));
        } else {
            return RebornResult.success();
        }
    }

}
