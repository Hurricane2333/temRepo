package com.ld.reborn.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.service.ArticleService;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.vo.ArticleVO;
import com.ld.reborn.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


/**
 * <p>
 * 文章表 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/article")
public class ArticleController {

    @Autowired
    private ArticleService articleService;


    /**
     * 保存文章
     */
    @LoginCheck(1)
    @PostMapping("/saveArticle")
    public RebornResult saveArticle(@Validated @RequestBody ArticleVO articleVO) {
        RebornCache.remove(CommonConst.USER_ARTICLE_LIST + RebornUtil.getUserId().toString());
        RebornCache.remove(CommonConst.ARTICLE_LIST);
        RebornCache.remove(CommonConst.SORT_ARTICLE_LIST);
        return articleService.saveArticle(articleVO);
    }


    /**
     * 删除文章
     */
    @GetMapping("/deleteArticle")
    @LoginCheck(1)
    public RebornResult deleteArticle(@RequestParam("id") Integer id) {
        RebornCache.remove(CommonConst.USER_ARTICLE_LIST + RebornUtil.getUserId().toString());
        RebornCache.remove(CommonConst.ARTICLE_LIST);
        RebornCache.remove(CommonConst.SORT_ARTICLE_LIST);
        return articleService.deleteArticle(id);
    }


    /**
     * 更新文章
     */
    @PostMapping("/updateArticle")
    @LoginCheck(1)
    public RebornResult updateArticle(@Validated @RequestBody ArticleVO articleVO) {
        RebornCache.remove(CommonConst.ARTICLE_LIST);
        RebornCache.remove(CommonConst.SORT_ARTICLE_LIST);
        return articleService.updateArticle(articleVO);
    }


    /**
     * 查询文章List
     */
    @PostMapping("/listArticle")
    public RebornResult<Page> listArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listArticle(baseRequestVO);
    }

    /**
     * 查询分类文章List
     */
    @GetMapping("/listSortArticle")
    public RebornResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        return articleService.listSortArticle();
    }

    /**
     * 查询文章
     */
    @GetMapping("/getArticleById")
    public RebornResult<ArticleVO> getArticleById(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return articleService.getArticleById(id, password);
    }
}

