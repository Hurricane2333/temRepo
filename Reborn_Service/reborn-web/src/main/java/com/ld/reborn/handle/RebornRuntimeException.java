package com.ld.reborn.handle;

public class RebornRuntimeException extends RuntimeException {

    private String msg;

    public RebornRuntimeException() {
        super();
    }

    public RebornRuntimeException(String msg) {
        super(msg);
        this.msg = msg;
    }


    public RebornRuntimeException(Throwable cause) {
        super(cause);
        this.msg = cause.getMessage();
    }

    public RebornRuntimeException(String msg, Throwable cause) {
        super(msg, cause);
        this.msg = msg;
    }


    public String getMsg() {
        return msg;
    }
}
