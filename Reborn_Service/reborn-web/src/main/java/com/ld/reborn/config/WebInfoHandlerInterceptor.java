package com.ld.reborn.config;

import com.alibaba.fastjson.JSON;
import com.ld.reborn.entity.WebInfo;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.constants.CommonConst;
import com.ld.reborn.utils.cache.RebornCache;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WebInfoHandlerInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        WebInfo webInfo = (WebInfo) RebornCache.get(CommonConst.WEB_INFO);
        if (webInfo == null || !webInfo.getStatus()) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(RebornResult.fail(CodeMsg.SYSTEM_REPAIR.getCode(), CodeMsg.SYSTEM_REPAIR.getMsg())));
            return false;
        } else {
            return true;
        }
    }
}
