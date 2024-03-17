package com.coco.boot.common;

import lombok.Data;

import java.io.Serializable;


@Data
public class R<T>  implements Serializable {

    /**
     * 响应代码
     */
    private Integer code;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应结果
     */
    private T data;


    public static <T> R<T> result(Boolean success, int code, T data, String msg) {
        R<T> rb = new R<>();
        rb.setSuccess(success);
        rb.setCode(code);
        rb.setData(data);
        rb.setMessage(msg);
        return rb;
    }


    public static <T> R<T> success(Boolean success) {
       return success ? success(): error(null);
    }

    public static <T> R<T> fail() {
        return error(null);
    }


    public static <T> R<T> success() {
        return success("");
    }


    public static <T> R<T> success(String message, T data) {
        return result(true, 200, data, message);
    }


    public static <T> R<T> success(T data) {
        return result(true, 200, data, "操作成功");
    }


    public static <T> R<T> success(String message) {
        return result(true, 200, null, message);
    }



    public static <T> R<T> error(Integer code, String message) {
        return result(false,code,  null,message);
    }


    public static <T> R<T> error(T data) {
        return result(false,500, data,"操作失败");
    }

    public static <T> R<T> error(String message, T data) {
        return result(false,500, data,message);
    }












}
