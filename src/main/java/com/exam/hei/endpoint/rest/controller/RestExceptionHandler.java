package com.exam.hei.endpoint.rest.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.exam.hei.endpoint.rest.model.Error;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ConflictException;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundException e) {
    return toError(NOT_FOUND, "NotFoundException", e.getMessage());
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<Error> handleBadRequest(BadRequestException e) {
    return toError(BAD_REQUEST, "BadRequestException", e.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Error> handleForbidden(ForbiddenException e) {
    return toError(FORBIDDEN, "ForbiddenException", e.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Error> handleConflict(ConflictException e) {
    return toError(CONFLICT, "ConflictException", e.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Error> handleBadCredentials(BadCredentialsException e) {
    return toError(UNAUTHORIZED, "UnauthorizedException", e.getMessage());
  }

  private ResponseEntity<Error> toError(HttpStatus status, String type, String message) {
    return ResponseEntity.status(status).body(Error.builder().type(type).message(message).build());
  }
}
