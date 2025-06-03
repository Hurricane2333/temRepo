package com.ld.reborn.im.http.service.impl;

import com.ld.reborn.im.http.entity.ImChatUserMessage;
import com.ld.reborn.im.http.dao.ImChatUserMessageMapper;
import com.ld.reborn.im.http.service.ImChatUserMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 单聊记录 服务实现类
 * </p>
 */
@Service
public class ImChatUserMessageServiceImpl extends ServiceImpl<ImChatUserMessageMapper, ImChatUserMessage> implements ImChatUserMessageService {

}
