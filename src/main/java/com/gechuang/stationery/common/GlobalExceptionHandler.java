package com.gechuang.stationery.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.unprocessableEntity()
                .body(RestResponse.fail(ErrorCode.VALIDATION_422.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestResponse<Void>> handleConstraint(ConstraintViolationException exception) {
        return ResponseEntity.unprocessableEntity()
                .body(RestResponse.fail(ErrorCode.VALIDATION_422.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<RestResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(statusOf(exception.getErrorCode()))
                .body(RestResponse.fail(exception.getErrorCode().getCode(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RestResponse<Void>> handleBusiness(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(RestResponse.fail(ErrorCode.BIZ_409.getCode(), exception.getMessage()));
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUTH_401 -> HttpStatus.UNAUTHORIZED;
            case AUTH_403 -> HttpStatus.FORBIDDEN;
            case AUTH_423 -> HttpStatus.LOCKED;
            case VALIDATION_422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_FOUND_404 -> HttpStatus.NOT_FOUND;
            case BIZ_409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
