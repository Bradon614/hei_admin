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
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";

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

  /**
   * What the database refuses once the services have had their say.
   *
   * <p>Without this the violation reaches the servlet container and comes back as a 500, which is
   * what a duplicate email used to answer. Not every violation is a conflict, so the SQLState
   * decides: a reference already taken is a 409, a value the column cannot hold is a 400. The
   * message stays generic — the constraint name is for the logs, not for the caller.
   *
   * <p>The Thymeleaf screens never reach here: they catch the exception in the controller method to
   * re-render their form instead of answering JSON.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Error> handleDataIntegrityViolation(DataIntegrityViolationException e) {
    var sqlState = sqlStateOf(e);
    if (UNIQUE_VIOLATION.equals(sqlState)) {
      return toError(CONFLICT, "ConflictException", "This reference or email is already taken");
    }
    if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
      return toError(CONFLICT, "ConflictException", "This value conflicts with related data");
    }
    return toError(BAD_REQUEST, "BadRequestException", "A submitted value is not storable as is");
  }

  private static String sqlStateOf(Throwable throwable) {
    for (var cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException) {
        return sqlException.getSQLState();
      }
    }
    return null;
  }

  private ResponseEntity<Error> toError(HttpStatus status, String type, String message) {
    return ResponseEntity.status(status).body(Error.builder().type(type).message(message).build());
  }
}
