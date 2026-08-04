package com.pixledger.api;

import com.pixledger.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse invalid(IllegalArgumentException exception, HttpServletRequest request) {
        log.warn("api.request rejected status=400 method={} uri={} reason={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        return new ApiErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiErrorResponse unauthorized(SecurityException exception, HttpServletRequest request) {
        log.warn("api.request rejected status=401 method={} uri={} reason={}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        return new ApiErrorResponse(exception.getMessage());
    }
}
