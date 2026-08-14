package com.exam.hei.model.exception;

/**
 * Thrown when a request is well formed but clashes with the state already recorded. Rendered as a
 * 409.
 *
 * <p>Used where a database constraint would otherwise fire and surface as a server error: an
 * overlapping group assignment, or a second track choice on a semester already covered.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
