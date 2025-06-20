package com.ld.reborn.utils.storage;

import com.ld.reborn.entity.User;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import com.ld.reborn.utils.RebornUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FileFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean doFilterFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        if (matcher.match("/resource/upload", httpServletRequest.getRequestURI())) {
            String token = RebornUtil.getToken();
            if (StringUtils.hasText(token)) {
                User user = (User) RebornCache.get(token);

                if (user != null) {
                    if (user.getId().intValue() == RebornUtil.getAdminUser().getId().intValue()) {
                        return false;
                    }

                    AtomicInteger atomicInteger = (AtomicInteger) RebornCache.get(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString());
                    if (atomicInteger == null) {
                        atomicInteger = new AtomicInteger();
                        RebornCache.put(CommonConst.SAVE_COUNT_USER_ID + user.getId().toString(), atomicInteger, CommonConst.SAVE_EXPIRE);
                    }
                    int userIdCount = atomicInteger.getAndIncrement();

                    String ip = RebornUtil.getIpAddr(RebornUtil.getRequest());
                    AtomicInteger atomic = (AtomicInteger) RebornCache.get(CommonConst.SAVE_COUNT_IP + ip);
                    if (atomic == null) {
                        atomic = new AtomicInteger();
                        RebornCache.put(CommonConst.SAVE_COUNT_IP + ip, atomic, CommonConst.SAVE_EXPIRE);
                    }
                    int ipCount = atomic.getAndIncrement();

                    return userIdCount >= CommonConst.SAVE_MAX_COUNT || ipCount >= CommonConst.SAVE_MAX_COUNT;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
