package com.ld.reborn.config;

import com.ld.reborn.enums.CodeMsg;
import lombok.Data;

import java.io.Serializable;

@Data
public class RebornResult<T> implements Serializable {

    private static final long serialVersionUI = 1L;

    private int code;
    private String message;
    private T data;
    private long currentTimeMillis = System.currentTimeMillis();

    public RebornResult() {
        this.code = 200;
    }

    public RebornResult(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public RebornResult(T data) {
        this.code = 200;
        this.data = data;
    }

    public RebornResult(String message) {
        this.code = 500;
        this.message = message;
    }

    public static <T> RebornResult<T> fail(String message) {
        return new RebornResult<>(message);
    }

    public static <T> RebornResult<T> fail(CodeMsg codeMsg) {
        return new RebornResult<>(codeMsg.getCode(), codeMsg.getMsg());
    }

    public static <T> RebornResult<T> fail(Integer code, String message) {
        return new RebornResult<>(code, message);
    }

    public static <T> RebornResult<T> success(T data) {
        return new RebornResult<>(data);
    }

    public static <T> RebornResult<T> success() {
        return new RebornResult<>();
    }
}
