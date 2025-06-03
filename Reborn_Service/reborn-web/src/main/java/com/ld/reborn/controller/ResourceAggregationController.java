package com.ld.reborn.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.ResourcePathMapper;
import com.ld.reborn.entity.ResourcePath;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.ResourcePathVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源聚合 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/webInfo")
public class ResourceAggregationController {

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    /**
     * 保存
     */
    @LoginCheck(0)
    @PostMapping("/saveResourcePath")
    public RebornResult saveResourcePath(@RequestBody ResourcePathVO resourcePathVO) {
        if (!StringUtils.hasText(resourcePathVO.getTitle()) || !StringUtils.hasText(resourcePathVO.getType())) {
            return RebornResult.fail("标题和资源类型不能为空！");
        }
        if (CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO.equals(resourcePathVO.getType())) {
            resourcePathVO.setRemark(RebornUtil.getAdminUser().getId().toString());
        }
        ResourcePath resourcePath = new ResourcePath();
        BeanUtils.copyProperties(resourcePathVO, resourcePath);
        resourcePathMapper.insert(resourcePath);
        return RebornResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteResourcePath")
    @LoginCheck(0)
    public RebornResult deleteResourcePath(@RequestParam("id") Integer id) {
        resourcePathMapper.deleteById(id);
        return RebornResult.success();
    }

    /**
     * 更新
     */
    @PostMapping("/updateResourcePath")
    @LoginCheck(0)
    public RebornResult updateResourcePath(@RequestBody ResourcePathVO resourcePathVO) {
        if (!StringUtils.hasText(resourcePathVO.getTitle()) || !StringUtils.hasText(resourcePathVO.getType())) {
            return RebornResult.fail("标题和资源类型不能为空！");
        }
        if (resourcePathVO.getId() == null) {
            return RebornResult.fail("Id不能为空！");
        }
        if (CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO.equals(resourcePathVO.getType())) {
            resourcePathVO.setRemark(RebornUtil.getAdminUser().getId().toString());
        }
        ResourcePath resourcePath = new ResourcePath();
        BeanUtils.copyProperties(resourcePathVO, resourcePath);
        resourcePathMapper.updateById(resourcePath);
        return RebornResult.success();
    }


    /**
     * 查询资源
     */
    @PostMapping("/listResourcePath")
    public RebornResult<Page> listResourcePath(@RequestBody BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<ResourcePath> wrapper = new LambdaQueryChainWrapper<>(resourcePathMapper);
        wrapper.eq(StringUtils.hasText(baseRequestVO.getResourceType()), ResourcePath::getType, baseRequestVO.getResourceType());
        wrapper.eq(StringUtils.hasText(baseRequestVO.getClassify()), ResourcePath::getClassify, baseRequestVO.getClassify());

        Integer userId = RebornUtil.getUserId();
        if (!RebornUtil.getAdminUser().getId().equals(userId)) {
            wrapper.eq(ResourcePath::getStatus, Boolean.TRUE);
        } else {
            wrapper.eq(baseRequestVO.getStatus() != null, ResourcePath::getStatus, baseRequestVO.getStatus());
        }

        OrderItem orderItem = new OrderItem();
        orderItem.setColumn(StringUtils.hasText(baseRequestVO.getOrder()) ? StrUtil.toUnderlineCase(baseRequestVO.getOrder()) : "create_time");
        orderItem.setAsc(!baseRequestVO.isDesc());
        List<OrderItem> orderItemList = new ArrayList<>();
        orderItemList.add(orderItem);
        baseRequestVO.setOrders(orderItemList);

        wrapper.page(baseRequestVO);

        List<ResourcePath> resourcePaths = baseRequestVO.getRecords();
        if (!CollectionUtils.isEmpty(resourcePaths)) {
            List<ResourcePathVO> resourcePathVOs = resourcePaths.stream().map(rp -> {
                ResourcePathVO resourcePathVO = new ResourcePathVO();
                BeanUtils.copyProperties(rp, resourcePathVO);
                return resourcePathVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(resourcePathVOs);
        }
        return RebornResult.success(baseRequestVO);
    }
}
