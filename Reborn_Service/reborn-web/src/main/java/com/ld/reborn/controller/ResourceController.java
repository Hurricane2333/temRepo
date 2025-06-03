package com.ld.reborn.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.entity.Resource;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.service.ResourceService;
import com.ld.reborn.utils.storage.StoreService;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.storage.FileStorageService;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.FileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源信息 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/resource")
public class ResourceController {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 保存
     */
    @PostMapping("/saveResource")
    @LoginCheck
    public RebornResult saveResource(@RequestBody Resource resource) {
        if (!StringUtils.hasText(resource.getType()) || !StringUtils.hasText(resource.getPath())) {
            return RebornResult.fail("资源类型和资源路径不能为空！");
        }
        Resource re = new Resource();
        re.setPath(resource.getPath());
        re.setType(resource.getType());
        re.setSize(resource.getSize());
        re.setOriginalName(resource.getOriginalName());
        re.setMimeType(resource.getMimeType());
        re.setStoreType(resource.getStoreType());
        re.setUserId(RebornUtil.getUserId());
        resourceService.save(re);
        return RebornResult.success();
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @LoginCheck
    public RebornResult<String> upload(@RequestParam("file") MultipartFile file, FileVO fileVO) {
        if (file == null || !StringUtils.hasText(fileVO.getType()) || !StringUtils.hasText(fileVO.getRelativePath())) {
            return RebornResult.fail("文件和资源类型和资源路径不能为空！");
        }

        fileVO.setFile(file);
        StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
        FileVO result = storeService.saveFile(fileVO);

        Resource re = new Resource();
        re.setPath(result.getVisitPath());
        re.setType(fileVO.getType());
        re.setSize(Integer.valueOf(Long.toString(file.getSize())));
        re.setMimeType(file.getContentType());
        re.setStoreType(fileVO.getStoreType());
        re.setOriginalName(fileVO.getOriginalName());
        re.setUserId(RebornUtil.getUserId());
        resourceService.save(re);
        return RebornResult.success(result.getVisitPath());
    }

    /**
     * 删除
     */
    @PostMapping("/deleteResource")
    @LoginCheck(0)
    public RebornResult deleteResource(@RequestParam("path") String path) {
        Resource resource = resourceService.lambdaQuery().select(Resource::getStoreType).eq(Resource::getPath, path).one();
        if (resource == null) {
            return RebornResult.fail("文件不存在：" + path);
        }

        StoreService storeService = fileStorageService.getFileStorageByStoreType(resource.getStoreType());
        storeService.deleteFile(Collections.singletonList(path));
        return RebornResult.success();
    }

    /**
     * 查询表情包
     */
    @GetMapping("/getImageList")
    @LoginCheck
    public RebornResult<List<String>> getImageList() {
        List<Resource> list = resourceService.lambdaQuery().select(Resource::getPath)
                .eq(Resource::getType, CommonConst.PATH_TYPE_INTERNET_MEME)
                .eq(Resource::getStatus, RebornEnum.STATUS_ENABLE.getCode())
                .eq(Resource::getUserId, RebornUtil.getAdminUser().getId())
                .orderByDesc(Resource::getCreateTime)
                .list();
        List<String> paths = list.stream().map(Resource::getPath).collect(Collectors.toList());
        return RebornResult.success(paths);
    }

    /**
     * 查询资源
     */
    @PostMapping("/listResource")
    @LoginCheck(0)
    public RebornResult<Page> listResource(@RequestBody BaseRequestVO baseRequestVO) {
        resourceService.lambdaQuery()
                .eq(StringUtils.hasText(baseRequestVO.getResourceType()), Resource::getType, baseRequestVO.getResourceType())
                .orderByDesc(Resource::getCreateTime).page(baseRequestVO);
        return RebornResult.success(baseRequestVO);
    }

    /**
     * 修改资源状态
     */
    @GetMapping("/changeResourceStatus")
    @LoginCheck(0)
    public RebornResult changeResourceStatus(@RequestParam("id") Integer id, @RequestParam("flag") Boolean flag) {
        resourceService.lambdaUpdate().eq(Resource::getId, id).set(Resource::getStatus, flag).update();
        return RebornResult.success();
    }
}

