package com.exam.hei.model.exception;

/**
 * Thrown when an authenticated caller reaches a resource that is not theirs. Rendered as a 403.
 *
 * <p>Distinct from a role restriction, which never reaches the business code: those are refused by
 * the security filter chain. This one covers the rules that compare the caller to the resource,
 * such as a student reading a record.
 */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
