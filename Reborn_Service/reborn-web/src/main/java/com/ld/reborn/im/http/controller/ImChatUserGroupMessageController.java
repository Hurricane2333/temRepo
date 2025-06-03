package com.ld.reborn.im.http.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.User;
import com.ld.reborn.im.http.entity.ImChatGroup;
import com.ld.reborn.im.http.entity.ImChatGroupUser;
import com.ld.reborn.im.http.entity.ImChatUserGroupMessage;
import com.ld.reborn.im.http.service.ImChatGroupService;
import com.ld.reborn.im.http.service.ImChatGroupUserService;
import com.ld.reborn.im.http.service.ImChatUserGroupMessageService;
import com.ld.reborn.im.http.vo.GroupMessageVO;
import com.ld.reborn.im.websocket.ImConfigConst;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.RebornUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 群聊记录 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/imChatUserGroupMessage")
public class ImChatUserGroupMessageController {

    @Autowired
    private ImChatUserGroupMessageService imChatUserGroupMessageService;

    @Autowired
    private ImChatGroupUserService imChatGroupUserService;

    @Autowired
    private ImChatGroupService imChatGroupService;

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 获取群消息（只获取前四十条）
     */
    @GetMapping("/listGroupMessage")
    @LoginCheck
    public RebornResult<Page> listGroupMessage(@RequestParam(value = "current", defaultValue = "1") Long current,
                                               @RequestParam(value = "size", defaultValue = "40") Long size,
                                               @RequestParam(value = "groupId") Integer groupId) {
        Page<ImChatUserGroupMessage> page = new Page<>();
        page.setCurrent(current);
        page.setSize(size);

        Integer userId = RebornUtil.getUserId();

        ImChatGroup chatGroup = imChatGroupService.getById(groupId);
        if (chatGroup == null) {
            return RebornResult.fail("群组不存在！");
        }

        if (chatGroup.getGroupType().intValue() == ImConfigConst.GROUP_COMMON) {
            LambdaQueryChainWrapper<ImChatGroupUser> groupLambdaQuery = imChatGroupUserService.lambdaQuery();
            groupLambdaQuery.eq(ImChatGroupUser::getGroupId, groupId);
            groupLambdaQuery.eq(ImChatGroupUser::getUserId, userId);
            groupLambdaQuery.in(ImChatGroupUser::getUserStatus, ImConfigConst.GROUP_USER_STATUS_PASS, ImConfigConst.GROUP_USER_STATUS_SILENCE);
            Integer count = groupLambdaQuery.count();
            if (count < 1) {
                return RebornResult.fail("未加群！");
            }
        }

        LambdaQueryChainWrapper<ImChatUserGroupMessage> lambdaQuery = imChatUserGroupMessageService.lambdaQuery();
        lambdaQuery.eq(ImChatUserGroupMessage::getGroupId, groupId);
        lambdaQuery.orderByDesc(ImChatUserGroupMessage::getCreateTime);
        Page<ImChatUserGroupMessage> result = lambdaQuery.page(page);
        List<ImChatUserGroupMessage> records = result.getRecords();
        Collections.reverse(records);
        if (CollectionUtils.isEmpty(records)) {
            return RebornResult.success(result);
        } else {
            List<GroupMessageVO> collect = records.stream().map(message -> {
                GroupMessageVO groupMessageVO = new GroupMessageVO();
                groupMessageVO.setContent(message.getContent());
                groupMessageVO.setFromId(message.getFromId());
                groupMessageVO.setToId(message.getToId());
                groupMessageVO.setId(message.getId());
                groupMessageVO.setGroupId(message.getGroupId());
                groupMessageVO.setCreateTime(message.getCreateTime());
                Integer messageUserId = message.getFromId();
                User user = commonQuery.getUser(messageUserId);
                if (user != null) {
                    groupMessageVO.setUsername(user.getUsername());
                    groupMessageVO.setAvatar(user.getAvatar());
                }
                return groupMessageVO;
            }).collect(Collectors.toList());
            Page<GroupMessageVO> resultVO = new Page<>();
            resultVO.setRecords(collect);
            resultVO.setTotal(result.getTotal());
            resultVO.setCurrent(result.getCurrent());
            resultVO.setSize(result.getSize());
            return RebornResult.success(resultVO);
        }
    }
}

