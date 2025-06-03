package com.ld.reborn.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.LabelMapper;
import com.ld.reborn.dao.SortMapper;
import com.ld.reborn.entity.Label;
import com.ld.reborn.entity.Sort;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.cache.RebornCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 分类标签 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/webInfo")
public class SortLabelController {

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 获取分类标签信息
     */
    @GetMapping("/getSortInfo")
    public RebornResult<List<Sort>> getSortInfo() {
        return RebornResult.success(commonQuery.getSortInfo());
    }

    /**
     * 保存
     */
    @PostMapping("/saveSort")
    @LoginCheck(0)
    public RebornResult saveSort(@RequestBody Sort sort) {
        if (!StringUtils.hasText(sort.getSortName()) || !StringUtils.hasText(sort.getSortDescription())) {
            return RebornResult.fail("分类名称和分类描述不能为空！");
        }

        if (sort.getPriority() == null) {
            return RebornResult.fail("分类必须配置优先级！");
        }

        sortMapper.insert(sort);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteSort")
    @LoginCheck(0)
    public RebornResult deleteSort(@RequestParam("id") Integer id) {
        sortMapper.deleteById(id);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateSort")
    @LoginCheck(0)
    public RebornResult updateSort(@RequestBody Sort sort) {
        sortMapper.updateById(sort);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listSort")
    public RebornResult<List<Sort>> listSort() {
        return RebornResult.success(new LambdaQueryChainWrapper<>(sortMapper).list());
    }


    /**
     * 保存
     */
    @PostMapping("/saveLabel")
    @LoginCheck(0)
    public RebornResult saveLabel(@RequestBody Label label) {
        if (!StringUtils.hasText(label.getLabelName()) || !StringUtils.hasText(label.getLabelDescription()) || label.getSortId() == null) {
            return RebornResult.fail("标签名称和标签描述和分类Id不能为空！");
        }
        labelMapper.insert(label);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteLabel")
    @LoginCheck(0)
    public RebornResult deleteLabel(@RequestParam("id") Integer id) {
        labelMapper.deleteById(id);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateLabel")
    @LoginCheck(0)
    public RebornResult updateLabel(@RequestBody Label label) {
        labelMapper.updateById(label);
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listLabel")
    public RebornResult<List<Label>> listLabel() {
        return RebornResult.success(new LambdaQueryChainWrapper<>(labelMapper).list());
    }


    /**
     * 查询List
     */
    @GetMapping("/listSortAndLabel")
    public RebornResult<Map> listSortAndLabel() {
        Map<String, List> map = new HashMap<>();
        map.put("sorts", new LambdaQueryChainWrapper<>(sortMapper).list());
        map.put("labels", new LambdaQueryChainWrapper<>(labelMapper).list());
        return RebornResult.success(map);
    }
}
