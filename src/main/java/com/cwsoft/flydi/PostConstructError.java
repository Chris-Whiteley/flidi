package com.cwsoft.flydi;

public class PostConstructError extends IllegalStateException {
    public PostConstructError(String msg) {
        super(msg);
    }

    public PostConstructError(String msg, Throwable cause) {
        super(msg, cause);
    }

}
