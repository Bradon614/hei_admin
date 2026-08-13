package com.exam.hei.repository.model;

/**
 * Role carried by a user account.
 *
 * <p>Mirrors the {@code Role} enumeration of doc/api.yml, and is deliberately declared once rather
 * than duplicated between the persistence and the REST layers: the values are identical on both
 * sides, so a second enumeration would only add a mapping with no meaning of its own.
 */
public enum Role {
  STUDENT,
  TEACHER,
  ADMIN
}
