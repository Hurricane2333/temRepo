package com.ld.reborn.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.aop.ResourceCheck;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.dao.ArticleMapper;
import com.ld.reborn.dao.LabelMapper;
import com.ld.reborn.dao.SortMapper;
import com.ld.reborn.entity.*;
import com.ld.reborn.enums.CommentTypeEnum;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.service.ArticleService;
import com.ld.reborn.service.UserService;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.mail.MailUtil;
import com.ld.reborn.vo.ArticleVO;
import com.ld.reborn.vo.BaseRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 文章表 服务实现类
 * </p>
 */
@Service
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private UserService userService;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Value("${user.subscribe.format}")
    private String subscribeFormat;

    @Override
    public RebornResult<Page<Article>> saveArticle(ArticleVO articleVO) {
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return RebornResult.fail("请设置文章密码！");
        }
        Article article = new Article();
        if (StringUtils.hasText(articleVO.getArticleCover())) {
            article.setArticleCover(articleVO.getArticleCover());
        }
        if (StringUtils.hasText(articleVO.getVideoUrl())) {
            article.setVideoUrl(articleVO.getVideoUrl());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            article.setPassword(articleVO.getPassword());
            article.setTips(articleVO.getTips());
        }
        article.setViewStatus(articleVO.getViewStatus());
        article.setCommentStatus(articleVO.getCommentStatus());
        article.setRecommendStatus(articleVO.getRecommendStatus());
        article.setArticleTitle(articleVO.getArticleTitle());
        article.setArticleContent(articleVO.getArticleContent());
        article.setSortId(articleVO.getSortId());
        article.setLabelId(articleVO.getLabelId());
        article.setUserId(RebornUtil.getUserId());
        save(article);

        RebornCache.remove(CommonConst.SORT_INFO);

        try {
            if (articleVO.getViewStatus()) {
                List<User> users = userService.lambdaQuery().select(User::getEmail, User::getSubscribe).eq(User::getUserStatus, RebornEnum.STATUS_ENABLE.getCode()).list();
                List<String> emails = users.stream().filter(u -> {
                    List<Integer> sub = JSON.parseArray(u.getSubscribe(), Integer.class);
                    return !CollectionUtils.isEmpty(sub) && sub.contains(articleVO.getLabelId());
                }).map(User::getEmail).collect(Collectors.toList());

                if (!CollectionUtils.isEmpty(emails)) {
                    LambdaQueryChainWrapper<Label> wrapper = new LambdaQueryChainWrapper<>(labelMapper);
                    Label label = wrapper.select(Label::getLabelName).eq(Label::getId, articleVO.getLabelId()).one();
                    String text = getSubscribeMail(label.getLabelName(), articleVO.getArticleTitle());
                    WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
                    mailUtil.sendMailMessage(emails, "您有一封来自" + (webInfo == null ? "REBORN" : webInfo.getWebName()) + "的回执！", text);
                }
            }
        } catch (Exception e) {
            log.error("订阅邮件发送失败：", e);
        }
        return RebornResult.success();
    }

    private String getSubscribeMail(String labelName, String articleTitle) {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "REBORN" : webInfo.getWebName());
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.notificationMail, RebornUtil.getAdminUser().getUsername()),
                RebornUtil.getAdminUser().getUsername(),
                String.format(subscribeFormat, labelName, articleTitle),
                "",
                webName);
    }

    @Override
    public RebornResult<Page<Article>> deleteArticle(Integer id) {
        Integer userId = RebornUtil.getUserId();
        lambdaUpdate().eq(Article::getId, id)
                .eq(Article::getUserId, userId)
                .remove();
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }

    @Override
    public RebornResult<Page<Article>> updateArticle(ArticleVO articleVO) {
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && !StringUtils.hasText(articleVO.getPassword())) {
            return RebornResult.fail("请设置文章密码！");
        }

        Integer userId = RebornUtil.getUserId();
        LambdaUpdateChainWrapper<Article> updateChainWrapper = lambdaUpdate()
                .eq(Article::getId, articleVO.getId())
                .eq(Article::getUserId, userId)
                .set(Article::getLabelId, articleVO.getLabelId())
                .set(Article::getSortId, articleVO.getSortId())
                .set(Article::getArticleTitle, articleVO.getArticleTitle())
                .set(Article::getUpdateBy, RebornUtil.getUsername())
                .set(Article::getUpdateTime, LocalDateTime.now())
                .set(Article::getVideoUrl, StringUtils.hasText(articleVO.getVideoUrl()) ? articleVO.getVideoUrl() : null)
                .set(Article::getArticleContent, articleVO.getArticleContent());

        if (StringUtils.hasText(articleVO.getArticleCover())) {
            updateChainWrapper.set(Article::getArticleCover, articleVO.getArticleCover());
        }
        if (articleVO.getCommentStatus() != null) {
            updateChainWrapper.set(Article::getCommentStatus, articleVO.getCommentStatus());
        }
        if (articleVO.getRecommendStatus() != null) {
            updateChainWrapper.set(Article::getRecommendStatus, articleVO.getRecommendStatus());
        }
        if (articleVO.getViewStatus() != null && !articleVO.getViewStatus() && StringUtils.hasText(articleVO.getPassword())) {
            updateChainWrapper.set(Article::getPassword, articleVO.getPassword());
            updateChainWrapper.set(StringUtils.hasText(articleVO.getTips()), Article::getTips, articleVO.getTips());
        }
        if (articleVO.getViewStatus() != null) {
            updateChainWrapper.set(Article::getViewStatus, articleVO.getViewStatus());
        }
        updateChainWrapper.update();
        RebornCache.remove(CommonConst.SORT_INFO);
        return RebornResult.success();
    }

    @Override
    public RebornResult<Page<Article>> listArticle(BaseRequestVO<Article> baseRequestVO) {
        List<Integer> ids = null;
        List<List<Integer>> idList = null;
        if (StringUtils.hasText(baseRequestVO.getArticleSearch())) {
            idList = commonQuery.getArticleIds(baseRequestVO.getArticleSearch());
            ids = idList.stream().flatMap(Collection::stream).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(ids)) {
                baseRequestVO.setRecords(new ArrayList<>());
                return RebornResult.success(baseRequestVO);
            }
        }

        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.in(!CollectionUtils.isEmpty(ids), Article::getId, ids);
        lambdaQuery.like(StringUtils.hasText(baseRequestVO.getSearchKey()), Article::getArticleTitle, baseRequestVO.getSearchKey());
        lambdaQuery.eq(baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus(), Article::getRecommendStatus, RebornEnum.STATUS_ENABLE.getCode());


        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        } else if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        lambdaQuery.orderByDesc(Article::getCreateTime);

        lambdaQuery.page(baseRequestVO);

        List<Article> records = baseRequestVO.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> articles = new ArrayList<>();
            List<ArticleVO> titles = new ArrayList<>();
            List<ArticleVO> contents = new ArrayList<>();

            for (Article article : records) {
                if (article.getArticleContent().length() > CommonConst.SUMMARY) {
                    article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY).replace("`", "").replace("#", "").replace(">", ""));
                }
                ArticleVO articleVO = buildArticleVO(article, false);
                articleVO.setHasVideo(StringUtils.hasText(articleVO.getVideoUrl()));
                articleVO.setPassword(null);
                articleVO.setVideoUrl(null);
                if (CollectionUtils.isEmpty(ids)) {
                    articles.add(articleVO);
                } else if (idList.get(0).contains(articleVO.getId())) {
                    titles.add(articleVO);
                } else if (idList.get(1).contains(articleVO.getId())) {
                    contents.add(articleVO);
                }
            }

            List<ArticleVO> collect = new ArrayList<>();
            collect.addAll(articles);
            collect.addAll(titles);
            collect.addAll(contents);
            Page<ArticleVO> resultPage = new Page<>(
                    baseRequestVO.getCurrent(),
                    baseRequestVO.getSize(),
                    baseRequestVO.getTotal()
            );
            resultPage.setRecords(collect);
        }
        return RebornResult.success(baseRequestVO);
    }

    @Override
    @ResourceCheck(CommonConst.RESOURCE_ARTICLE_DOC)
    public RebornResult<ArticleVO> getArticleById(Integer id, String password) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id);

        Article article = lambdaQuery.one();
        if (article == null) {
            return RebornResult.success();
        }
        if (!article.getViewStatus() && (!StringUtils.hasText(password) || !password.equals(article.getPassword()))) {
            return RebornResult.fail("密码错误" + (StringUtils.hasText(article.getTips()) ? article.getTips() : "请联系作者获取密码"));
        }
        articleMapper.updateViewCount(id);
        article.setPassword(null);
        if (StringUtils.hasText(article.getVideoUrl())) {
            article.setVideoUrl(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).encryptBase64(article.getVideoUrl()));
        }
        ArticleVO articleVO = buildArticleVO(article, false);
        return RebornResult.success(articleVO);
    }

    @Override
    public RebornResult<Page<Article>> listAdminArticle(BaseRequestVO<Article> baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.select(Article.class, a -> !a.getColumn().equals("article_content"));
        if (!isBoss) {
            lambdaQuery.eq(Article::getUserId, RebornUtil.getUserId());
        } else {
            if (baseRequestVO.getUserId() != null) {
                lambdaQuery.eq(Article::getUserId, baseRequestVO.getUserId());
            }
        }
        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.like(Article::getArticleTitle, baseRequestVO.getSearchKey());
        }
        if (baseRequestVO.getRecommendStatus() != null && baseRequestVO.getRecommendStatus()) {
            lambdaQuery.eq(Article::getRecommendStatus, RebornEnum.STATUS_ENABLE.getCode());
        }

        if (baseRequestVO.getLabelId() != null) {
            lambdaQuery.eq(Article::getLabelId, baseRequestVO.getLabelId());
        }

        if (baseRequestVO.getSortId() != null) {
            lambdaQuery.eq(Article::getSortId, baseRequestVO.getSortId());
        }

        lambdaQuery.orderByDesc(Article::getCreateTime).page(baseRequestVO);

        List<Article> records = baseRequestVO.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            List<ArticleVO> articleVOs = baseRequestVO.getRecords().stream().
                    map(article -> {
                        article.setPassword(null); // 清除敏感字段
                        return buildArticleVO(article, true); // 转换为VO对象
                    })
                    .collect(Collectors.toList());
            Page<ArticleVO> resultPage = new Page<>(
                    baseRequestVO.getCurrent(), // 保留分页信息
                    baseRequestVO.getSize(),
                    baseRequestVO.getTotal()
            );
            resultPage.setRecords(articleVOs);
        }
        return RebornResult.success(baseRequestVO);
    }

    @Override
    public RebornResult<ArticleVO> getArticleByIdForUser(Integer id) {
        LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery();
        lambdaQuery.eq(Article::getId, id).eq(Article::getUserId, RebornUtil.getUserId());
        Article article = lambdaQuery.one();
        if (article == null) {
            return RebornResult.fail("文章不存在！");
        }
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        return RebornResult.success(articleVO);
    }

    @Override
    public RebornResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        Map<Integer, List<ArticleVO>> result = (Map<Integer, List<ArticleVO>>) RebornCache.get(CommonConst.SORT_ARTICLE_LIST);
        if (result != null) {
            return RebornResult.success(result);
        }

        synchronized (CommonConst.SORT_ARTICLE_LIST.intern()) {
            result = (Map<Integer, List<ArticleVO>>) RebornCache.get(CommonConst.SORT_ARTICLE_LIST);
            if (result == null) {
                Map<Integer, List<ArticleVO>> map = new HashMap<>();

                List<Sort> sorts = new LambdaQueryChainWrapper<>(sortMapper).select(Sort::getId).list();
                for (Sort sort : sorts) {
                    LambdaQueryChainWrapper<Article> lambdaQuery = lambdaQuery()
                            .eq(Article::getSortId, sort.getId())
                            .orderByDesc(Article::getCreateTime)
                            .last("limit 6");
                    List<Article> articleList = lambdaQuery.list();
                    if (CollectionUtils.isEmpty(articleList)) {
                        continue;
                    }

                    List<ArticleVO> articleVOList = articleList.stream().map(article -> {
                        if (article.getArticleContent().length() > CommonConst.SUMMARY) {
                            article.setArticleContent(article.getArticleContent().substring(0, CommonConst.SUMMARY).replace("`", "").replace("#", "").replace(">", ""));
                        }

                        ArticleVO vo = buildArticleVO(article, false);
                        vo.setHasVideo(StringUtils.hasText(article.getVideoUrl()));
                        vo.setPassword(null);
                        vo.setVideoUrl(null);
                        return vo;
                    }).collect(Collectors.toList());
                    map.put(sort.getId(), articleVOList);
                }

                RebornCache.put(CommonConst.SORT_ARTICLE_LIST, map, CommonConst.TOKEN_INTERVAL);
                return RebornResult.success(map);
            } else {
                return RebornResult.success(result);
            }
        }
    }

    private ArticleVO buildArticleVO(Article article, Boolean isAdmin) {
        ArticleVO articleVO = new ArticleVO();
        BeanUtils.copyProperties(article, articleVO);
        if (!isAdmin) {
            if (!StringUtils.hasText(articleVO.getArticleCover())) {
                articleVO.setArticleCover(RebornUtil.getRandomCover(articleVO.getId().toString()));
            }
        }

        User user = commonQuery.getUser(articleVO.getUserId());
        if (user != null && StringUtils.hasText(user.getUsername())) {
            articleVO.setUsername(user.getUsername());
        } else if (!isAdmin) {
            articleVO.setUsername(RebornUtil.getRandomName(articleVO.getUserId().toString()));
        }
        if (articleVO.getCommentStatus()) {
            articleVO.setCommentCount(commonQuery.getCommentCount(articleVO.getId(), CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()));
        } else {
            articleVO.setCommentCount(0);
        }

        List<Sort> sortInfo = commonQuery.getSortInfo();
        if (!CollectionUtils.isEmpty(sortInfo)) {
            for (Sort s : sortInfo) {
                if (s.getId().intValue() == articleVO.getSortId().intValue()) {
                    Sort sort = new Sort();
                    BeanUtils.copyProperties(s, sort);
                    sort.setLabels(null);
                    articleVO.setSort(sort);
                    if (!CollectionUtils.isEmpty(s.getLabels())) {
                        for (int j = 0; j < s.getLabels().size(); j++) {
                            Label l = s.getLabels().get(j);
                            if (l.getId().intValue() == articleVO.getLabelId().intValue()) {
                                Label label = new Label();
                                BeanUtils.copyProperties(l, label);
                                articleVO.setLabel(label);
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return articleVO;
    }
}
