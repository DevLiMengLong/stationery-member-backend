package com.gechuang.stationery.common;

public enum ErrorCode {
    SUCCESS("0", "success"),
    AUTH_401("AUTH_401", "未登录或登录已过期"),
    AUTH_403("AUTH_403", "无权限"),
    AUTH_423("AUTH_423", "账号已停用"),
    VALIDATION_422("VALIDATION_422", "参数校验失败"),
    BIZ_409("BIZ_409", "业务冲突"),
    NOT_FOUND_404("NOT_FOUND_404", "数据不存在"),
    SYS_500("SYS_500", "系统异常");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
