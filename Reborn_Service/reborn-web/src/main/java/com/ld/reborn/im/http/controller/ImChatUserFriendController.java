package com.ld.reborn.im.http.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.entity.User;
import com.ld.reborn.im.http.entity.ImChatUserFriend;
import com.ld.reborn.im.http.service.ImChatUserFriendService;
import com.ld.reborn.im.http.vo.UserFriendVO;
import com.ld.reborn.im.websocket.ImConfigConst;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.RebornUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 好友 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/imChatUserFriend")
public class ImChatUserFriendController {

    @Autowired
    private ImChatUserFriendService userFriendService;

    @Autowired
    private CommonQuery commonQuery;

    /**
     * 添加好友申请
     */
    @GetMapping("/addFriend")
    @LoginCheck
    public RebornResult addFriend(@RequestParam("friendId") Integer friendId, @RequestParam(value = "remark", required = false) String remark) {
        User friend = commonQuery.getUser(friendId);
        if (friend == null) {
            return RebornResult.fail("用户不存在！");
        }

        Integer userId = RebornUtil.getUserId();

        Integer count = userFriendService.lambdaQuery()
                .and(wrapper -> wrapper.eq(ImChatUserFriend::getUserId, userId).eq(ImChatUserFriend::getFriendId, friendId))
                .or(wrapper -> wrapper.eq(ImChatUserFriend::getFriendId, userId).eq(ImChatUserFriend::getUserId, friendId))
                .count();
        if (count > 0) {
            return RebornResult.success();
        }

        ImChatUserFriend imChatFriend = new ImChatUserFriend();
        imChatFriend.setUserId(friendId);
        imChatFriend.setFriendId(userId);
        imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_NOT_VERIFY);
        imChatFriend.setRemark(remark);
        userFriendService.save(imChatFriend);
        return RebornResult.success();
    }

    /**
     * 查询好友
     */
    @GetMapping("/getFriend")
    @LoginCheck
    public RebornResult<List<UserFriendVO>> getFriend(@RequestParam(value = "friendStatus", required = false) Integer friendStatus) {
        Integer userId = RebornUtil.getUserId();
        LambdaQueryChainWrapper<ImChatUserFriend> wrapper = userFriendService.lambdaQuery().eq(ImChatUserFriend::getUserId, userId);
        if (friendStatus != null) {
            wrapper.eq(ImChatUserFriend::getFriendStatus, friendStatus);
        }

        List<ImChatUserFriend> userFriends = wrapper.orderByDesc(ImChatUserFriend::getCreateTime).list();
        List<UserFriendVO> userFriendVOS = new ArrayList<>(userFriends.size());
        userFriends.forEach(userFriend -> {
            User friend = commonQuery.getUser(userFriend.getFriendId());
            if (friend != null) {
                UserFriendVO userFriendVO = new UserFriendVO();
                userFriendVO.setId(userFriend.getId());
                userFriendVO.setUserId(userFriend.getUserId());
                userFriendVO.setFriendId(userFriend.getFriendId());
                userFriendVO.setCreateTime(userFriend.getCreateTime());
                userFriendVO.setRemark(StringUtils.hasText(userFriend.getRemark()) ? userFriend.getRemark() : friend.getUsername());
                userFriendVO.setFriendStatus(userFriend.getFriendStatus());
                userFriendVO.setUsername(friend.getUsername());
                userFriendVO.setAvatar(friend.getAvatar());
                userFriendVO.setGender(friend.getGender());
                userFriendVO.setIntroduction(friend.getIntroduction());
                userFriendVOS.add(userFriendVO);
            }
        });
        return RebornResult.success(userFriendVOS);
    }

    /**
     * 修改好友
     * <p>
     * 朋友状态[-1:审核不通过或者删除好友，0:未审核，1:审核通过]
     */
    @GetMapping("/changeFriend")
    @LoginCheck
    public RebornResult changeFriend(@RequestParam("friendId") Integer friendId,
                                     @RequestParam(value = "friendStatus", required = false) Integer friendStatus,
                                     @RequestParam(value = "remark", required = false) String remark) {
        Integer userId = RebornUtil.getUserId();
        ImChatUserFriend userFriend = userFriendService.lambdaQuery()
                .eq(ImChatUserFriend::getUserId, userId)
                .eq(ImChatUserFriend::getFriendId, friendId).one();

        if (userFriend == null) {
            return RebornResult.fail("好友不存在！");
        }

        if (friendStatus != null) {
            if (friendStatus == ImConfigConst.FRIEND_STATUS_PASS) {
                userFriendService.lambdaUpdate()
                        .set(ImChatUserFriend::getFriendStatus, friendStatus)
                        .eq(ImChatUserFriend::getId, userFriend.getId()).update();

                ImChatUserFriend imChatFriend = new ImChatUserFriend();
                imChatFriend.setUserId(friendId);
                imChatFriend.setFriendId(userId);
                imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
                userFriendService.save(imChatFriend);
            }

            if (friendStatus == ImConfigConst.FRIEND_STATUS_BAN) {
                userFriendService.removeById(userFriend.getId());
                userFriendService.lambdaUpdate()
                        .eq(ImChatUserFriend::getUserId, friendId)
                        .eq(ImChatUserFriend::getFriendId, userId).remove();
            }
        }

        if (StringUtils.hasText(remark)) {
            userFriendService.lambdaUpdate()
                    .set(ImChatUserFriend::getRemark, remark)
                    .eq(ImChatUserFriend::getId, userFriend.getId()).update();
        }


        return RebornResult.success();
    }
}

