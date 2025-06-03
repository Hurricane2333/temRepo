package com.ld.reborn.aop;

import com.ld.reborn.config.RebornResult;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.entity.User;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.enums.RebornEnum;
import com.ld.reborn.handle.RebornLoginException;
import com.ld.reborn.handle.RebornRuntimeException;
import com.ld.reborn.utils.*;
import com.ld.reborn.utils.cache.RebornCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Aspect
@Component
@Order(0)
@Slf4j
public class LoginCheckAspect {

    @Around("@annotation(loginCheck)")
    public Object around(ProceedingJoinPoint joinPoint, LoginCheck loginCheck) throws Throwable {
        String token = RebornUtil.getToken();
        if (!StringUtils.hasText(token)) {
            throw new RebornLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        User user = (User) RebornCache.get(token);

        if (user == null) {
            throw new RebornLoginException(CodeMsg.LOGIN_EXPIRED.getMsg());
        }

        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            if (loginCheck.value() == RebornEnum.USER_TYPE_ADMIN.getCode() || loginCheck.value() == RebornEnum.USER_TYPE_DEV.getCode()) {
                return RebornResult.fail("请输入管理员账号！");
            }
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            log.info("请求IP：" + RebornUtil.getIpAddr(RebornUtil.getRequest()));
            if (loginCheck.value() == RebornEnum.USER_TYPE_ADMIN.getCode() && user.getId().intValue() != CommonConst.ADMIN_USER_ID) {
                return RebornResult.fail("请输入管理员账号！");
            }
        } else {
            throw new RebornLoginException(CodeMsg.NOT_LOGIN.getMsg());
        }

        if (loginCheck.value() < user.getUserType()) {
            throw new RebornRuntimeException("权限不足！");
        }

        //重置过期时间
        String userId = user.getId().toString();
        boolean flag1 = false;
        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            flag1 = RebornCache.get(CommonConst.USER_TOKEN_INTERVAL + userId) == null;
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            flag1 = RebornCache.get(CommonConst.ADMIN_TOKEN_INTERVAL + userId) == null;
        }

        if (flag1) {
            synchronized (userId.intern()) {
                boolean flag2 = false;
                if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
                    flag2 = RebornCache.get(CommonConst.USER_TOKEN_INTERVAL + userId) == null;
                } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                    flag2 = RebornCache.get(CommonConst.ADMIN_TOKEN_INTERVAL + userId) == null;
                }

                if (flag2) {
                    RebornCache.put(token, user, CommonConst.TOKEN_EXPIRE);
                    if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
                        RebornCache.put(CommonConst.USER_TOKEN + userId, token, CommonConst.TOKEN_EXPIRE);
                        RebornCache.put(CommonConst.USER_TOKEN_INTERVAL + userId, token, CommonConst.TOKEN_INTERVAL);
                    } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                        RebornCache.put(CommonConst.ADMIN_TOKEN + userId, token, CommonConst.TOKEN_EXPIRE);
                        RebornCache.put(CommonConst.ADMIN_TOKEN_INTERVAL + userId, token, CommonConst.TOKEN_INTERVAL);
                    }
                }
            }
        }
        return joinPoint.proceed();
    }
}
