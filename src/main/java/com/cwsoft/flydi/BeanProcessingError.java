package com.cwsoft.flydi;

public class BeanProcessingError extends IllegalStateException {
    public BeanProcessingError(String msg) {
        super(msg);
    }

    public BeanProcessingError(String msg, Throwable cause) {
        super(msg, cause);
    }

}
