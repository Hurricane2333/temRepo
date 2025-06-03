package com.ld.reborn.config;

import com.alibaba.fastjson.JSON;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.utils.CommonQuery;
import com.ld.reborn.utils.RebornUtil;
import com.ld.reborn.utils.storage.FileFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class RebornFilter extends OncePerRequestFilter {

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private FileFilter fileFilter;

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        if (!"OPTIONS".equals(httpServletRequest.getMethod())) {
            try {
                commonQuery.saveHistory(RebornUtil.getIpAddr(httpServletRequest));
            } catch (Exception e) {
            }

            if (fileFilter.doFilterFile(httpServletRequest, httpServletResponse)) {
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setContentType("application/json;charset=UTF-8");
                httpServletResponse.getWriter().write(JSON.toJSONString(RebornResult.fail(CodeMsg.PARAMETER_ERROR.getCode(), CodeMsg.PARAMETER_ERROR.getMsg())));
                return;
            }
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}
