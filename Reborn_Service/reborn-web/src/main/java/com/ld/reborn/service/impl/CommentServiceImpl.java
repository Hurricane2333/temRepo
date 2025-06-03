package com.ld.reborn.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.ArticleMapper;
import com.ld.reborn.dao.CommentMapper;
import com.ld.reborn.entity.Article;
import com.ld.reborn.entity.Comment;
import com.ld.reborn.entity.User;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.enums.CommentTypeEnum;
import com.ld.reborn.service.CommentService;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.mail.MailSendUtil;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.CommentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 文章评论表 服务实现类
 * </p>
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private MailSendUtil mailSendUtil;

    @Override
    public RebornResult saveComment(CommentVO commentVO) {
        if (CommentTypeEnum.getEnumByCode(commentVO.getType()) == null) {
            return RebornResult.fail("评论来源类型不存在！");
        }
        Article one = null;
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            one = articleWrapper.eq(Article::getId, commentVO.getSource()).select(Article::getUserId, Article::getArticleTitle, Article::getCommentStatus).one();

            if (one == null) {
                return RebornResult.fail("文章不存在");
            } else {
                if (!one.getCommentStatus()) {
                    return RebornResult.fail("评论功能已关闭！");
                }
            }
        }


        Comment comment = new Comment();
        comment.setSource(commentVO.getSource());
        comment.setType(commentVO.getType());
        comment.setCommentContent(commentVO.getCommentContent());
        comment.setParentCommentId(commentVO.getParentCommentId());
        comment.setFloorCommentId(commentVO.getFloorCommentId());
        comment.setParentUserId(commentVO.getParentUserId());
        comment.setUserId(RebornUtil.getUserId());
        if (StringUtils.hasText(commentVO.getCommentInfo())) {
            comment.setCommentInfo(commentVO.getCommentInfo());
        }
        save(comment);

        try {
            mailSendUtil.sendCommentMail(commentVO, one, this);
        } catch (Exception e) {
            log.error("发送评论邮件失败：", e);
        }

        return RebornResult.success();
    }

    @Override
    public RebornResult deleteComment(Integer id) {
        Integer userId = RebornUtil.getUserId();
        lambdaUpdate().eq(Comment::getId, id)
                .eq(Comment::getUserId, userId)
                .remove();
        return RebornResult.success();
    }

    @Override
    public RebornResult<BaseRequestVO> listComment(BaseRequestVO baseRequestVO) {
        if (baseRequestVO.getSource() == null || !StringUtils.hasText(baseRequestVO.getCommentType())) {
            return RebornResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(baseRequestVO.getCommentType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            Article one = articleWrapper.eq(Article::getId, baseRequestVO.getSource()).select(Article::getCommentStatus).one();

            if (one != null && !one.getCommentStatus()) {
                return RebornResult.fail("评论功能已关闭！");
            }
        }


        if (baseRequestVO.getFloorCommentId() == null) {
            lambdaQuery().eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, baseRequestVO.getCommentType()).eq(Comment::getParentCommentId, CommonConst.FIRST_COMMENT).orderByDesc(Comment::getCreateTime).page(baseRequestVO);
            List<Comment> comments = baseRequestVO.getRecords();
            if (CollectionUtils.isEmpty(comments)) {
                return RebornResult.success(baseRequestVO);
            }
            List<CommentVO> commentVOs = comments.stream().map(c -> {
                CommentVO commentVO = buildCommentVO(c);
                Page page = new Page(1, 5);
                lambdaQuery().eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, baseRequestVO.getCommentType()).eq(Comment::getFloorCommentId, c.getId()).orderByAsc(Comment::getCreateTime).page(page);
                List<Comment> childComments = page.getRecords();
                if (childComments != null) {
                    List<CommentVO> ccVO = childComments.stream().map(cc -> buildCommentVO(cc)).collect(Collectors.toList());
                    page.setRecords(ccVO);
                }
                commentVO.setChildComments(page);
                return commentVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(commentVOs);
        } else {
            lambdaQuery().eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, baseRequestVO.getCommentType()).eq(Comment::getFloorCommentId, baseRequestVO.getFloorCommentId()).orderByAsc(Comment::getCreateTime).page(baseRequestVO);
            List<Comment> childComments = baseRequestVO.getRecords();
            if (CollectionUtils.isEmpty(childComments)) {
                return RebornResult.success(baseRequestVO);
            }
            List<CommentVO> ccVO = childComments.stream().map(cc -> buildCommentVO(cc)).collect(Collectors.toList());
            baseRequestVO.setRecords(ccVO);
        }
        return RebornResult.success(baseRequestVO);
    }

    @Override
    public RebornResult<Page> listAdminComment(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Comment> wrapper = lambdaQuery();
        if (isBoss) {
            if (baseRequestVO.getSource() != null) {
                wrapper.eq(Comment::getSource, baseRequestVO.getSource());
            }
            if (StringUtils.hasText(baseRequestVO.getCommentType())) {
                wrapper.eq(Comment::getType, baseRequestVO.getCommentType());
            }
            wrapper.orderByDesc(Comment::getCreateTime).page(baseRequestVO);
        } else {
            List<Integer> userArticleIds = commonQuery.getUserArticleIds(RebornUtil.getUserId());
            if (CollectionUtils.isEmpty(userArticleIds)) {
                baseRequestVO.setTotal(0);
                baseRequestVO.setRecords(new ArrayList());
            } else {
                if (baseRequestVO.getSource() != null) {
                    wrapper.eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode());
                } else {
                    wrapper.eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()).in(Comment::getSource, userArticleIds);
                }
                wrapper.orderByDesc(Comment::getCreateTime).page(baseRequestVO);
            }
        }
        return RebornResult.success(baseRequestVO);
    }

    private CommentVO buildCommentVO(Comment c) {
        CommentVO commentVO = new CommentVO();
        BeanUtils.copyProperties(c, commentVO);

        User user = commonQuery.getUser(commentVO.getUserId());
        if (user != null) {
            commentVO.setAvatar(user.getAvatar());
            commentVO.setUsername(user.getUsername());
        }

        if (!StringUtils.hasText(commentVO.getUsername())) {
            commentVO.setUsername(RebornUtil.getRandomName(commentVO.getUserId().toString()));
        }

        if (commentVO.getParentUserId() != null) {
            User u = commonQuery.getUser(commentVO.getParentUserId());
            if (u != null) {
                commentVO.setParentUsername(u.getUsername());
            }
            if (!StringUtils.hasText(commentVO.getParentUsername())) {
                commentVO.setParentUsername(RebornUtil.getRandomName(commentVO.getParentUserId().toString()));
            }
        }
        return commentVO;
    }
}
