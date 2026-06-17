package com.gechuang.stationery.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final String traceId;
    private final String timestamp;

    private RestResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static <T> RestResponse<T> success(T data) {
        return new RestResponse<>(true, "0", "success", data);
    }

    public static <T> RestResponse<T> success() {
        return new RestResponse<>(true, "0", "success", null);
    }

    public static <T> RestResponse<T> fail(String code, String message) {
        return new RestResponse<>(false, code, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
