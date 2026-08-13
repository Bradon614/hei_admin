package com.exam.hei.model.exception;

/** Thrown when the caller sends something the business rules cannot accept. Rendered as a 400. */
public class BadRequestException extends RuntimeException {

  public BadRequestException(String message) {
    super(message);
  }
}
