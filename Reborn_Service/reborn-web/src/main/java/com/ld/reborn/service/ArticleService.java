package com.ld.reborn.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.Article;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.reborn.vo.ArticleVO;
import com.ld.reborn.vo.BaseRequestVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 文章表 服务类
 * </p>
 */
public interface ArticleService extends IService<Article> {

    RebornResult<Page<Article>> saveArticle(ArticleVO articleVO);

    RebornResult<Page<Article>> deleteArticle(Integer id);

    RebornResult<Page<Article>> updateArticle(ArticleVO articleVO);

    RebornResult<Page<Article>> listArticle(BaseRequestVO<Article> baseRequestVO);

    RebornResult<ArticleVO> getArticleById(Integer id, String password);

    RebornResult<Page<Article>> listAdminArticle(BaseRequestVO<Article> baseRequestVO, Boolean isBoss);

    RebornResult<ArticleVO> getArticleByIdForUser(Integer id);

    RebornResult<Map<Integer, List<ArticleVO>>> listSortArticle();
}
