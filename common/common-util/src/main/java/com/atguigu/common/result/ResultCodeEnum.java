package com.atguigu.common.result;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {

    SUCCESS(200,"成功"),
    FAIL(201, "失败"),
    LOGIN_ERROR(202,"认证失败"),

    SERVICE_ERROR(203, "服务异常"),

    DATA_ERROR(204, "数据异常"),

    LOGIN_AUTH(205, "未登陆"),
    PERMISSION(206, "没有权限"),
    ACCOUNT_STOP(27,"没有授权");

    private Integer code;

    private String message;

    private ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

}
