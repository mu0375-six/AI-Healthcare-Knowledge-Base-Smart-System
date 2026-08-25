package com.healthkb.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
    private final int code;
    private final HttpStatus status;

    public AppException(int code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static AppException badRequest(String message) {
        return new AppException(400, message, HttpStatus.BAD_REQUEST);
    }

    public static AppException unauthorized(String message) {
        return new AppException(401, message, HttpStatus.UNAUTHORIZED);
    }

    public static AppException forbidden(String message) {
        return new AppException(403, message, HttpStatus.FORBIDDEN);
    }

    public static AppException notFound(String message) {
        return new AppException(404, message, HttpStatus.NOT_FOUND);
    }

    public static AppException conflict(String message) {
        return new AppException(409, message, HttpStatus.CONFLICT);
    }
}
