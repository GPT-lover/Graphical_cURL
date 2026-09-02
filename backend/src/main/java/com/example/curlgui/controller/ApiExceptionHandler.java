package com.example.curlgui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.curlgui.dto.ErrorResponseDto;
import com.example.curlgui.service.ConflictException;
import com.example.curlgui.service.InvalidRequestException;
import com.example.curlgui.service.NotFoundException;

/**
 * Turns the shared service exceptions into JSON error responses, so the
 * collection / saved-request controllers can stay free of try/catch.
 *
 * <p>{@code @RestControllerAdvice} registers these handlers for every
 * {@code @RestController}. It only fires for exceptions a controller method lets
 * <em>propagate</em> - the older controllers (send / import / export) still catch
 * their own exceptions locally, so their behaviour is unchanged.
 *
 * <p>Messages come straight from the exceptions and are written to be safe to
 * show the user; no stack traces are exposed.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponseDto> badRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDto(ex.getMessage(), null));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDto> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDto(ex.getMessage(), null));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDto> conflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto(ex.getMessage(), null));
    }
}
