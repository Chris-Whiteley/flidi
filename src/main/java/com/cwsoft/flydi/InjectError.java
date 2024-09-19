package com.cwsoft.flydi;

public class InjectError extends IllegalStateException {
    public InjectError(String msg) {
        super(msg);
    }

    public  InjectError(String msg, Throwable cause) {
        super(msg, cause);
    }

}
