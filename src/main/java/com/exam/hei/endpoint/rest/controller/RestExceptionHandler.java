package com.exam.hei.endpoint.rest.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.exam.hei.endpoint.rest.model.Error;
import com.exam.hei.model.exception.BadRequestException;
import com.exam.hei.model.exception.ForbiddenException;
import com.exam.hei.model.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders business failures as the {@code Error} payload of doc/api.yml.
 *
 * <p>Only the statuses the specification actually declares are mapped here. Authentication and
 * authorization failures never reach this advice: they are raised inside the security filter chain,
 * before the dispatcher servlet, and are rendered by the entry point and the access denied handler
 * of the security package.
 */
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

  /**
   * Covers the rules that compare the caller to the resource, such as a student reaching another
   * student's record. Role restrictions never get here: the filter chain refuses them earlier.
   */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Error> handleForbidden(ForbiddenException e) {
    return toError(FORBIDDEN, "ForbiddenException", e.getMessage());
  }

  private ResponseEntity<Error> toError(HttpStatus status, String type, String message) {
    return ResponseEntity.status(status).body(Error.builder().type(type).message(message).build());
  }
}
