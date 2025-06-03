package com.ld.reborn.aop;

import com.ld.reborn.entity.User;
import com.ld.reborn.handle.RebornRuntimeException;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.RebornUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicInteger;


@Aspect
@Component
@Order(1)
@Slf4j
public class SaveCheckAspect {

    @Around("@annotation(saveCheck)")
    public Object around(ProceedingJoinPoint joinPoint, SaveCheck saveCheck) throws Throwable {
        boolean flag = false;

        String token = RebornUtil.getToken();
        if (StringUtils.hasText(token)) {
            User user = (User) RebornCache.get(token);
            if (user != null) {
                if (user.getId().intValue() == RebornUtil.getAdminUser().getId().intValue()) {
                    return joinPoint.proceed();
                }

                AtomicInteger atomicInteger = (AtomicInteger) RebornCache.get(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString());
                if (atomicInteger == null) {
                    atomicInteger = new AtomicInteger();
                    RebornCache.put(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString(), atomicInteger, CommonConst.SAVE_EXPIRE);
                }
                int userIdCount = atomicInteger.getAndIncrement();
                if (userIdCount >= CommonConst.SAVE_MAX_COUNT) {
                    log.info("用户保存超限：" + user.getId().toString() + "，次数：" + userIdCount);
                    flag = true;
                }
            }
        }

        String ip = RebornUtil.getIpAddr(RebornUtil.getRequest());
        AtomicInteger atomic = (AtomicInteger) RebornCache.get(CommonConst.SAVE_COUNT_IP + ip);
        if (atomic == null) {
            atomic = new AtomicInteger();
            RebornCache.put(CommonConst.SAVE_COUNT_IP + ip, atomic, CommonConst.SAVE_EXPIRE);
        }
        int ipCount = atomic.getAndIncrement();
        if (ipCount > CommonConst.SAVE_MAX_COUNT) {
            log.info("IP保存超限：" + ip + "，次数：" + ipCount);
            flag = true;
        }

        if (flag) {
            throw new RebornRuntimeException("今日提交次数已用尽，请一天后再来！");
        }

        return joinPoint.proceed();
    }
}
