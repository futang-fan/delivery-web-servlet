package com.deliveryweb.util;

/**
 * 统一响应封装：code / msg / data 三字段标准 JSON。
 */
public class Result {

    private int code;
    private String msg;
    private Object data;

    public Result() {
    }

    public Result(int code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static Result ok() {
        return new Result(200, "操作成功", null);
    }

    public static Result ok(Object data) {
        return new Result(200, "操作成功", data);
    }

    public static Result ok(String msg, Object data) {
        return new Result(200, msg, data);
    }

    public static Result fail(String msg) {
        return new Result(400, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
