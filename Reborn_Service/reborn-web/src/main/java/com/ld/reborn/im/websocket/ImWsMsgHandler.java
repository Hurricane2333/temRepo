package com.ld.reborn.im.websocket;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.entity.User;
import com.ld.reborn.im.http.entity.ImChatGroupUser;
import com.ld.reborn.im.http.entity.ImChatUserGroupMessage;
import com.ld.reborn.im.http.entity.ImChatUserMessage;
import com.ld.reborn.im.http.service.ImChatGroupUserService;
import com.ld.reborn.im.http.service.ImChatUserMessageService;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.utils.lock.SetWithLock;
import org.tio.websocket.common.WsRequest;
import org.tio.websocket.common.WsResponse;
import org.tio.websocket.server.handler.IWsMsgHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ImWsMsgHandler implements IWsMsgHandler {

    @Autowired
    private ImChatGroupUserService imChatGroupUserService;

    @Autowired
    private ImChatUserMessageService imChatUserMessageService;

    @Autowired
    private MessageCache messageCache;

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 握手时走这个方法，业务可以在这里获取cookie，request等
     * 对httpResponse参数进行补充并返回，如果返回null表示不想和对方建立连接
     * 对于大部分业务，该方法只需要一行代码：return httpResponse;
     */
    @Override
    public HttpResponse handshake(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) {
        String token = httpRequest.getParam(CommonConst.TOKEN_HEADER);

        if (!StringUtils.hasText(token)) {
            return null;
        }

        User user = (User) RebornCache.get(token);

        if (user == null) {
            return null;
        }

        log.info("握手成功：用户ID：{}, 用户名：{}", user.getId(), user.getUsername());

        return httpResponse;
    }

    /**
     * 握手成功后触发该方法
     */
    @Override
    public void onAfterHandshaked(HttpRequest httpRequest, HttpResponse httpResponse, ChannelContext channelContext) {
        String token = httpRequest.getParam(CommonConst.TOKEN_HEADER);
        User user = (User) RebornCache.get(token);
        Tio.closeUser(channelContext.tioConfig, user.getId().toString(), null);
        Tio.bindUser(channelContext, user.getId().toString());

        List<ImChatUserMessage> userMessages = imChatUserMessageService.lambdaQuery().eq(ImChatUserMessage::getToId, user.getId())
                .eq(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_FALSE)
                .orderByAsc(ImChatUserMessage::getCreateTime).list();

        if (!CollectionUtils.isEmpty(userMessages)) {
            List<Long> ids = new ArrayList<>();
            userMessages.forEach(userMessage -> {
                ids.add(userMessage.getId());
                ImMessage imMessage = new ImMessage();
                imMessage.setContent(userMessage.getContent());
                imMessage.setFromId(userMessage.getFromId());
                imMessage.setToId(userMessage.getToId());
                imMessage.setMessageType(ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode());
                User friend = commonQuery.getUser(userMessage.getFromId());
                if (friend != null) {
                    imMessage.setAvatar(friend.getAvatar());
                }
                WsResponse wsResponse = WsResponse.fromText(JSON.toJSONString(imMessage), ImConfigConst.CHARSET);
                Tio.sendToUser(channelContext.tioConfig, userMessage.getToId().toString(), wsResponse);
            });
            imChatUserMessageService.lambdaUpdate().in(ImChatUserMessage::getId, ids)
                    .set(ImChatUserMessage::getMessageStatus, ImConfigConst.USER_MESSAGE_STATUS_TRUE).update();

        }

        LambdaQueryChainWrapper<ImChatGroupUser> lambdaQuery = imChatGroupUserService.lambdaQuery();
        lambdaQuery.select(ImChatGroupUser::getGroupId);
        lambdaQuery.eq(ImChatGroupUser::getUserId, user.getId());
        lambdaQuery.in(ImChatGroupUser::getUserStatus, ImConfigConst.GROUP_USER_STATUS_PASS, ImConfigConst.GROUP_USER_STATUS_SILENCE);
        List<ImChatGroupUser> groupUsers = lambdaQuery.list();
        if (!CollectionUtils.isEmpty(groupUsers)) {
            groupUsers.forEach(groupUser -> Tio.bindGroup(channelContext, groupUser.getGroupId().toString()));
        }
    }

    @Override
    public Object onBytes(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) {
        return null;
    }

    @Override
    public Object onClose(WsRequest wsRequest, byte[] bytes, ChannelContext channelContext) {
        Tio.remove(channelContext, "连接关闭");
        return null;
    }

    @Override
    public Object onText(WsRequest wsRequest, String text, ChannelContext channelContext) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            log.info("收到信令消息: {}", text);
            ImMessage imMessage = JSON.parseObject(text, ImMessage.class);

            String content = StringUtil.removeHtml(imMessage.getContent());
            if (!StringUtils.hasText(content)) {
                return null;
            }
            imMessage.setContent(content);

            WsResponse wsResponse = WsResponse.fromText(JSON.toJSONString(imMessage), ImConfigConst.CHARSET);

            // 处理音视频通话相关消息
            if (imMessage.getMessageType().intValue() >= ImEnum.MESSAGE_TYPE_CALL_VIDEO.getCode() &&
                imMessage.getMessageType().intValue() <= ImEnum.MESSAGE_TYPE_CALL_ICE.getCode()) {
                log.info("收到音视频通话消息，准备转发...");
                log.info("消息类型: {}", imMessage.getMessageType());
                log.info("发送方ID: {}", imMessage.getFromId());
                log.info("接收方ID: {}", imMessage.getToId());
                log.info("消息内容: {}", imMessage.getContent());

                // 检查目标用户是否在线
                SetWithLock<ChannelContext> targetUser = Tio.getByUserid(channelContext.tioConfig, imMessage.getToId().toString());
                if (targetUser != null && targetUser.size() > 0) {
                    log.info("目标用户在线，准备发送消息");
                    Tio.sendToUser(channelContext.tioConfig, imMessage.getToId().toString(), wsResponse);
                    log.info("消息已转发");
                } else {
                    log.warn("目标用户不在线，无法发送消息");
                }
                //return null;
            }

            if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_SINGLE.getCode()) {
                //单聊
                log.info("收到单聊消息，准备转发...");
                ImChatUserMessage userMessage = new ImChatUserMessage();
                userMessage.setFromId(imMessage.getFromId());
                userMessage.setToId(imMessage.getToId());
                userMessage.setContent(imMessage.getContent());
                userMessage.setCreateTime(LocalDateTime.now());

                SetWithLock<ChannelContext> setWithLock = Tio.getByUserid(channelContext.tioConfig, imMessage.getToId().toString());
                if (setWithLock != null && setWithLock.size() > 0) {
                    Tio.sendToUser(channelContext.tioConfig, imMessage.getToId().toString(), wsResponse);
                    userMessage.setMessageStatus(ImConfigConst.USER_MESSAGE_STATUS_TRUE);
                } else {
                    userMessage.setMessageStatus(ImConfigConst.USER_MESSAGE_STATUS_FALSE);
                }
                messageCache.putUserMessage(userMessage);
                Tio.sendToUser(channelContext.tioConfig, imMessage.getFromId().toString(), wsResponse);
            } else if (imMessage.getMessageType().intValue() == ImEnum.MESSAGE_TYPE_MSG_GROUP.getCode()) {
                //群聊
                ImChatUserGroupMessage groupMessage = new ImChatUserGroupMessage();
                groupMessage.setContent(imMessage.getContent());
                groupMessage.setFromId(imMessage.getFromId());
                groupMessage.setGroupId(imMessage.getGroupId());
                groupMessage.setCreateTime(LocalDateTime.now());
                messageCache.putGroupMessage(groupMessage);

                SetWithLock<ChannelContext> setWithLock = Tio.getByGroup(channelContext.tioConfig, imMessage.getGroupId().toString());
                if (setWithLock != null && setWithLock.size() > 0) {
                    Tio.sendToGroup(channelContext.tioConfig, imMessage.getGroupId().toString(), wsResponse);
                }
            }
        } catch (Exception e) {
            log.error("解析消息失败：{}", e.getMessage());
        }
        return null;
    }
}
