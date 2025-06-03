package com.ld.reborn.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.aop.SaveCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.ResourcePathMapper;
import com.ld.reborn.entity.ResourcePath;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.vo.ResourcePathVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 资源聚合里的图片，其他接口在ResourceAggregationController
 * </p>
 */
@RestController
@RequestMapping("/webInfo")
public class PictureController {

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    /**
     * 查询爱情
     */
    @GetMapping("/listAdminLovePhoto")
    public RebornResult<List<Map<String, Object>>> listAdminLovePhoto() {
        QueryWrapper<ResourcePath> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("classify, count(*) as count")
                .eq("status", Boolean.TRUE)
                .eq("remark", RebornUtil.getAdminUser().getId().toString())
                .eq("type", CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO)
                .groupBy("classify");
        List<Map<String, Object>> maps = resourcePathMapper.selectMaps(queryWrapper);

        return RebornResult.success(maps);
    }

    /**
     * 保存爱情
     */
    @LoginCheck
    @SaveCheck
    @PostMapping("/saveLovePhoto")
    public RebornResult saveLovePhoto(@RequestBody ResourcePathVO resourcePathVO) {
        if (!StringUtils.hasText(resourcePathVO.getClassify()) || !StringUtils.hasText(resourcePathVO.getCover()) ||
                !StringUtils.hasText(resourcePathVO.getTitle())) {
            return RebornResult.fail("信息不全！");
        }
        ResourcePath lovePhoto = new ResourcePath();
        lovePhoto.setClassify(resourcePathVO.getClassify());
        lovePhoto.setTitle(resourcePathVO.getTitle());
        lovePhoto.setCover(resourcePathVO.getCover());
        lovePhoto.setRemark(RebornUtil.getUserId().toString());
        lovePhoto.setType(CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO);
        lovePhoto.setStatus(Boolean.FALSE);
        resourcePathMapper.insert(lovePhoto);
        return RebornResult.success();
    }
}
