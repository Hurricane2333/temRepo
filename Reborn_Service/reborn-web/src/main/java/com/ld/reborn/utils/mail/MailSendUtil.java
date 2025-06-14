package com.ld.reborn.utils.mail;

import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.entity.Article;
import com.ld.reborn.entity.Comment;
import com.ld.reborn.entity.User;
import com.ld.reborn.entity.WebInfo;
import com.ld.reborn.enums.CommentTypeEnum;
import com.ld.reborn.im.http.entity.ImChatUserMessage;
import com.ld.reborn.service.CommentService;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.vo.CommentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MailSendUtil {

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private MailUtil mailUtil;

    public void sendCommentMail(CommentVO commentVO, Article one, CommentService commentService) {
        List<String> mail = new ArrayList<>();
        String toName = "";
        if (commentVO.getParentUserId() != null) {
            User user = commonQuery.getUser(commentVO.getParentUserId());
            if (user != null && !user.getId().equals(RebornUtil.getUserId()) && StringUtils.hasText(user.getEmail())) {
                toName = user.getUsername();
                mail.add(user.getEmail());
            }
        } else {
            if (CommentTypeEnum.COMMENT_TYPE_MESSAGE.getCode().equals(commentVO.getType()) ||
                    CommentTypeEnum.COMMENT_TYPE_LOVE.getCode().equals(commentVO.getType())) {
                User adminUser = RebornUtil.getAdminUser();
                if (StringUtils.hasText(adminUser.getEmail()) && !Objects.equals(RebornUtil.getUserId(), adminUser.getId())) {
                    mail.add(adminUser.getEmail());
                }
            } else if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
                User user = commonQuery.getUser(one.getUserId());
                if (user != null && StringUtils.hasText(user.getEmail()) && !user.getId().equals(RebornUtil.getUserId())) {
                    mail.add(user.getEmail());
                }
            }
        }

        if (!CollectionUtils.isEmpty(mail)) {
            String sourceName = "";
            if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
                sourceName = one.getArticleTitle();
            }
            String commentMail = getCommentMail(commentVO.getType(), sourceName,
                    RebornUtil.getUsername(),
                    commentVO.getCommentContent(),
                    toName,
                    commentVO.getParentCommentId(), commentService);

            AtomicInteger count = (AtomicInteger) RebornCache.get(CommonConst.COMMENT_IM_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.COMMENT_IM_MAIL_COUNT) {
                WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", commentMail);
                if (count == null) {
                    RebornCache.put(CommonConst.COMMENT_IM_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            }
        }
    }

    /**
     * source：0留言 其他是文章标题
     * fromName：评论人
     * toName：被评论人
     */
    private String getCommentMail(String commentType, String source, String fromName, String fromContent, String toName, Integer toCommentId, CommentService commentService) {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "REBORN" : webInfo.getWebName());

        String mailType = "";
        String toMail = "";
        if (StringUtils.hasText(toName)) {
            mailType = String.format(MailUtil.replyMail, fromName);
            Comment toComment = commentService.lambdaQuery().select(Comment::getCommentContent).eq(Comment::getId, toCommentId).one();
            if (toComment != null) {
                toMail = String.format(MailUtil.originalText, toName, toComment.getCommentContent());
            }
        } else {
            if (CommentTypeEnum.COMMENT_TYPE_MESSAGE.getCode().equals(commentType)) {
                mailType = String.format(MailUtil.messageMail, fromName);
            } else if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentType)) {
                mailType = String.format(MailUtil.commentMail, source, fromName);
            } else if (CommentTypeEnum.COMMENT_TYPE_LOVE.getCode().equals(commentType)) {
                mailType = String.format(MailUtil.loveMail, fromName);
            }
        }

        return String.format(mailUtil.getMailText(),
                webName,
                mailType,
                fromName,
                fromContent,
                toMail,
                webName);
    }

    public void sendImMail(ImChatUserMessage message) {
        if (!message.getMessageStatus()) {
            List<String> mail = new ArrayList<>();
            String username = "";
            User toUser = commonQuery.getUser(message.getToId());
            if (toUser != null && StringUtils.hasText(toUser.getEmail())) {
                mail.add(toUser.getEmail());
            }
            User fromUser = commonQuery.getUser(message.getFromId());
            if (fromUser != null) {
                username = fromUser.getUsername();
            }

            if (!CollectionUtils.isEmpty(mail)) {
                String commentMail = getImMail(username, message.getContent());

                AtomicInteger count = (AtomicInteger) RebornCache.get(CommonConst.COMMENT_IM_MAIL + mail.get(0));
                if (count == null || count.get() < CommonConst.COMMENT_IM_MAIL_COUNT) {
                    WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
                    mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", commentMail);
                    if (count == null) {
                        RebornCache.put(CommonConst.COMMENT_IM_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                    } else {
                        count.incrementAndGet();
                    }
                }
            }
        }
    }

    private String getImMail(String fromName, String fromContent) {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "REBORN" : webInfo.getWebName());

        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.imMail, fromName),
                fromName,
                fromContent,
                "",
                webName);
    }
}
