package com.exam.hei.model.exception;

/** Thrown when a resource named by the caller does not exist. Rendered as a 404. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
