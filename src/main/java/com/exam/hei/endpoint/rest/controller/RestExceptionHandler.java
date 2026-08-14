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

/**
 * Renders business failures as the {@code Error} payload of doc/api.yml.
 *
 * <p>Only the statuses the specification actually declares are mapped here. Authentication and
 * authorization failures raised while checking a bearer token never reach this advice: they happen
 * inside the security filter chain, before the dispatcher servlet, and are rendered by the entry
 * point and the access denied handler of the security package. {@code POST /auth/login} is the one
 * exception: it runs as an ordinary public endpoint, so a wrong password surfaces as a plain {@link
 * BadCredentialsException} and is rendered here like any other failure.
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

  /**
   * Reported by the services before a database constraint has to fire, so that a legitimate clash
   * reads as a conflict rather than a server error.
   */
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
