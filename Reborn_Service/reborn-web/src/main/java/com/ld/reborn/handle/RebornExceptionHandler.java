package com.ld.reborn.handle;

import com.alibaba.fastjson.JSON;
import com.ld.reborn.config.RebornResult;
import com.ld.reborn.enums.CodeMsg;
import com.ld.reborn.utils.RebornUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class RebornExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public RebornResult handlerException(Exception ex) {
        log.error("请求URL-----------------" + RebornUtil.getRequest().getRequestURL());
        log.error("出错啦------------------", ex);
        if (ex instanceof RebornRuntimeException) {
            RebornRuntimeException e = (RebornRuntimeException) ex;
            return RebornResult.fail(e.getMessage());
        }

        if (ex instanceof RebornLoginException) {
            RebornLoginException e = (RebornLoginException) ex;
            return RebornResult.fail(300, e.getMessage());
        }

        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException e = (MethodArgumentNotValidException) ex;
            Map<String, String> collect = e.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
            return RebornResult.fail(JSON.toJSONString(collect));
        }

        if (ex instanceof MissingServletRequestParameterException) {
            return RebornResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        return RebornResult.fail(CodeMsg.FAIL);
    }
}
