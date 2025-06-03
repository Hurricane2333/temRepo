package com.ld.reborn.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.reborn.aop.LoginCheck;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.entity.*;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.im.websocket.TioUtil;
import com.ld.reborn.im.websocket.TioWebsocketStarter;
import com.ld.reborn.service.UserService;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.tio.core.Tio;

/**
 * <p>
 * 后台用户 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/admin")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * 查询用户
     */
    @PostMapping("/user/list")
    @LoginCheck(0)
    public RebornResult<Page> listUser(@RequestBody BaseRequestVO baseRequestVO) {
        return userService.listUser(baseRequestVO);
    }

    /**
     * 修改用户状态
     * <p>
     * flag = true：解禁
     * flag = false：封禁
     */
    @GetMapping("/user/changeUserStatus")
    @LoginCheck(0)
    public RebornResult changeUserStatus(@RequestParam("userId") Integer userId, @RequestParam("flag") Boolean flag) {
        if (userId.intValue() == RebornUtil.getAdminUser().getId().intValue()) {
            return RebornResult.fail("站长状态不能修改！");
        }

        LambdaUpdateChainWrapper<User> updateChainWrapper = userService.lambdaUpdate().eq(User::getId, userId);
        if (flag) {
            updateChainWrapper.eq(User::getUserStatus, RebornEnum.STATUS_DISABLE.getCode()).set(User::getUserStatus, RebornEnum.STATUS_ENABLE.getCode()).update();
        } else {
            updateChainWrapper.eq(User::getUserStatus, RebornEnum.STATUS_ENABLE.getCode()).set(User::getUserStatus, RebornEnum.STATUS_DISABLE.getCode()).update();
        }
        logout(userId);
        return RebornResult.success();
    }

    /**
     * 修改用户赞赏
     */
    @GetMapping("/user/changeUserAdmire")
    @LoginCheck(0)
    public RebornResult changeUserAdmire(@RequestParam("userId") Integer userId, @RequestParam("admire") String admire) {
        userService.lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getAdmire, admire)
                .update();
        RebornCache.remove(CommonConst.ADMIRE);
        return RebornResult.success();
    }

    /**
     * 修改用户类型
     */
    @GetMapping("/user/changeUserType")
    @LoginCheck(0)
    public RebornResult changeUserType(@RequestParam("userId") Integer userId, @RequestParam("userType") Integer userType) {
        if (userId.intValue() == RebornUtil.getAdminUser().getId().intValue()) {
            return RebornResult.fail("站长类型不能修改！");
        }

        if (userType != 0 && userType != 1 && userType != 2) {
            return RebornResult.fail(CodeMsg.PARAMETER_ERROR);
        }
        userService.lambdaUpdate().eq(User::getId, userId).set(User::getUserType, userType).update();

        logout(userId);
        return RebornResult.success();
    }

    private void logout(Integer userId) {
        if (RebornCache.get(CommonConst.ADMIN_TOKEN + userId) != null) {
            String token = (String) RebornCache.get(CommonConst.ADMIN_TOKEN + userId);
            RebornCache.remove(CommonConst.ADMIN_TOKEN + userId);
            RebornCache.remove(token);
        }

        if (RebornCache.get(CommonConst.USER_TOKEN + userId) != null) {
            String token = (String) RebornCache.get(CommonConst.USER_TOKEN + userId);
            RebornCache.remove(CommonConst.USER_TOKEN + userId);
            RebornCache.remove(token);
        }
        TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
        if (tioWebsocketStarter != null) {
            Tio.removeUser(tioWebsocketStarter.getServerTioConfig(), String.valueOf(userId), "remove user");
        }

    }
}
