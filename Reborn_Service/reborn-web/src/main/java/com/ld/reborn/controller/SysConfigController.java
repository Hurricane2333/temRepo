package com.ld.reborn.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.SysConfig;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 参数配置表 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/sysConfig")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 查询系统参数
     */
    @GetMapping("/listSysConfig")
    public RebornResult<Map<String, String>> listSysConfig() {
        LambdaQueryChainWrapper<SysConfig> wrapper = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper());
        List<SysConfig> sysConfigs = wrapper.eq(SysConfig::getConfigType, Integer.toString(RebornEnum.SYS_CONFIG_PUBLIC.getCode()))
                .list();
        Map<String, String> collect = sysConfigs.stream().collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        return RebornResult.success(collect);
    }

    /**
     * 保存或更新
     */
    @PostMapping("/saveOrUpdateConfig")
    @LoginCheck(0)
    public RebornResult saveConfig(@RequestBody SysConfig sysConfig) {
        if (!StringUtils.hasText(sysConfig.getConfigName()) ||
                !StringUtils.hasText(sysConfig.getConfigKey()) ||
                !StringUtils.hasText(sysConfig.getConfigType())) {
            return RebornResult.fail("请完善所有配置信息！");
        }
        String configType = sysConfig.getConfigType();
        if (!Integer.toString(RebornEnum.SYS_CONFIG_PUBLIC.getCode()).equals(configType) &&
                !Integer.toString(RebornEnum.SYS_CONFIG_PRIVATE.getCode()).equals(configType)) {
            return RebornResult.fail("配置类型不正确！");
        }
        sysConfigService.saveOrUpdate(sysConfig);
        return RebornResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteConfig")
    @LoginCheck(0)
    public RebornResult deleteConfig(@RequestParam("id") Integer id) {
        sysConfigService.removeById(id);
        return RebornResult.success();
    }

    /**
     * 查询
     */
    @GetMapping("/listConfig")
    @LoginCheck(0)
    public RebornResult<List<SysConfig>> listConfig() {
        return RebornResult.success(new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper()).list());
    }
}
