package com.ld.reborn.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.*;
import com.ld.reborn.service.ArticleService;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.vo.ArticleVO;
import com.ld.reborn.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 后台文章 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/admin")
public class AdminArticleController {

    @Autowired
    private ArticleService articleService;

    /**
     * 用户查询文章
     */
    @PostMapping("/article/user/list")
    @LoginCheck(1)
    public RebornResult<Page<Article>> listUserArticle(@RequestBody BaseRequestVO<Article> baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, false);
    }

    /**
     * Boss查询文章
     */
    @PostMapping("/article/boss/list")
    @LoginCheck(0)
    public RebornResult<Page> listBossArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, true);
    }

    /**
     * 修改文章状态
     */
    @GetMapping("/article/changeArticleStatus")
    @LoginCheck(1)
    public RebornResult changeArticleStatus(@RequestParam("articleId") Integer articleId,
                                            @RequestParam(value = "viewStatus", required = false) Boolean viewStatus,
                                            @RequestParam(value = "commentStatus", required = false) Boolean commentStatus,
                                            @RequestParam(value = "recommendStatus", required = false) Boolean recommendStatus) {
        LambdaUpdateChainWrapper<Article> updateChainWrapper = articleService.lambdaUpdate()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, RebornUtil.getUserId());
        if (viewStatus != null) {
            updateChainWrapper.set(Article::getViewStatus, viewStatus);
        }
        if (commentStatus != null) {
            updateChainWrapper.set(Article::getCommentStatus, commentStatus);
        }
        if (recommendStatus != null) {
            updateChainWrapper.set(Article::getRecommendStatus, recommendStatus);
        }
        updateChainWrapper.update();
        return RebornResult.success();
    }

    /**
     * 查询文章
     */
    @GetMapping("/article/getArticleById")
    @LoginCheck(1)
    public RebornResult<ArticleVO> getArticleByIdForUser(@RequestParam("id") Integer id) {
        return articleService.getArticleByIdForUser(id);
    }
}
