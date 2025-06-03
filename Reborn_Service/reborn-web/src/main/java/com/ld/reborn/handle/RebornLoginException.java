package com.ld.reborn.handle;

public class RebornLoginException extends RuntimeException {

    private String msg;

    public RebornLoginException() {
        super();
    }

    public RebornLoginException(String msg) {
        super(msg);
        this.msg = msg;
    }


    public RebornLoginException(Throwable cause) {
        super(cause);
        this.msg = cause.getMessage();
    }

    public RebornLoginException(String msg, Throwable cause) {
        super(msg, cause);
        this.msg = msg;
    }


    public String getMsg() {
        return msg;
    }
}
