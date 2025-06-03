package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 后台 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
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
    public PoetryResult<WebInfo> getWebInfo() {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            return PoetryResult.success(list.get(0));
        } else {
            return PoetryResult.success();
        }
    }

}
