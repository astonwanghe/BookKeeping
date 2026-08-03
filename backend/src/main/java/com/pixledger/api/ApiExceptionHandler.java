package com.pixledger.api;

import com.pixledger.dto.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse invalid(IllegalArgumentException exception) { return new ApiErrorResponse(exception.getMessage()); }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiErrorResponse unauthorized(SecurityException exception) { return new ApiErrorResponse(exception.getMessage()); }
}
