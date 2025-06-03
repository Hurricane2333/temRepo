package com.ld.reborn.controller;


import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.aop.SaveCheck;
import com.ld.reborn.service.CommentService;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.StringUtil;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.CommentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * <p>
 * 文章评论表 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/comment")
public class CommentController {


    @Autowired
    private CommentService commentService;

    @Autowired
    private CommonQuery commonQuery;


    /**
     * 保存评论
     */
    @PostMapping("/saveComment")
    @LoginCheck
    @SaveCheck
    public RebornResult saveComment(@Validated @RequestBody CommentVO commentVO) {
        String content = StringUtil.removeHtml(commentVO.getCommentContent());
        if (!StringUtils.hasText(content)) {
            return RebornResult.fail("评论内容不合法！");
        }
        commentVO.setCommentContent(content);

        RebornCache.remove(CommonConst.COMMENT_COUNT_CACHE + commentVO.getSource().toString() + "_" + commentVO.getType());
        return commentService.saveComment(commentVO);
    }


    /**
     * 删除评论
     */
    @GetMapping("/deleteComment")
    @LoginCheck
    public RebornResult deleteComment(@RequestParam("id") Integer id) {
        return commentService.deleteComment(id);
    }


    /**
     * 查询评论数量
     */
    @GetMapping("/getCommentCount")
    public RebornResult<Integer> getCommentCount(@RequestParam("source") Integer source, @RequestParam("type") String type) {
        return RebornResult.success(commonQuery.getCommentCount(source, type));
    }


    /**
     * 查询评论
     */
    @PostMapping("/listComment")
    public RebornResult<BaseRequestVO> listComment(@RequestBody BaseRequestVO baseRequestVO) {
        return commentService.listComment(baseRequestVO);
    }
}

