package com.ld.reborn.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.Comment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.reborn.vo.BaseRequestVO;
import com.ld.reborn.vo.CommentVO;


/**
 * <p>
 * 文章评论表 服务类
 * </p>
 *
 */
public interface CommentService extends IService<Comment> {

    RebornResult saveComment(CommentVO commentVO);

    RebornResult deleteComment(Integer id);

    RebornResult<BaseRequestVO> listComment(BaseRequestVO baseRequestVO);

    RebornResult<Page> listAdminComment(BaseRequestVO baseRequestVO, Boolean isBoss);
}
