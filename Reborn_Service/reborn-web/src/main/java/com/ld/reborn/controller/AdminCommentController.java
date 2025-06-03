package com.ld.reborn.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.Article;
import com.ld.reborn.entity.Comment;
import com.ld.reborn.enums.CommentTypeEnum;
import com.ld.reborn.service.ArticleService;
import com.ld.reborn.service.CommentService;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 后台评论 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/admin")
public class AdminCommentController {

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CommentService commentService;

    /**
     * 作者删除评论
     */
    @GetMapping("/comment/user/deleteComment")
    @LoginCheck(1)
    public RebornResult userDeleteComment(@RequestParam("id") Integer id) {
        Comment comment = commentService.lambdaQuery().select(Comment::getSource, Comment::getType).eq(Comment::getId, id).one();
        if (comment == null) {
            return RebornResult.success();
        }
        if (!CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(comment.getType())) {
            return RebornResult.fail("权限不足！");
        }
        Article one = articleService.lambdaQuery().eq(Article::getId, comment.getSource()).select(Article::getUserId).one();
        if (one == null || (RebornUtil.getUserId().intValue() != one.getUserId().intValue())) {
            return RebornResult.fail("权限不足！");
        }
        commentService.removeById(id);
        return RebornResult.success();
    }

    /**
     * Boss删除评论
     */
    @GetMapping("/comment/boss/deleteComment")
    @LoginCheck(0)
    public RebornResult bossDeleteComment(@RequestParam("id") Integer id) {
        commentService.removeById(id);
        return RebornResult.success();
    }

    /**
     * 用户查询评论
     */
    @PostMapping("/comment/user/list")
    @LoginCheck(1)
    public RebornResult<Page> listUserComment(@RequestBody BaseRequestVO baseRequestVO) {
        return commentService.listAdminComment(baseRequestVO, false);
    }

    /**
     * Boss查询评论
     */
    @PostMapping("/comment/boss/list")
    @LoginCheck(0)
    public RebornResult<Page> listBossComment(@RequestBody BaseRequestVO baseRequestVO) {
        return commentService.listAdminComment(baseRequestVO, true);
    }
}
