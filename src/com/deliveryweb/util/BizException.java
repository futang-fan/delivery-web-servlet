package com.deliveryweb.util;

/**
 * 业务异常：携带错误提示，由控制层统一捕获并转换为标准错误响应。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BizException(String message) {
        super(message);
    }

    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
