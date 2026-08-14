package com.exam.hei.model;

import com.exam.hei.model.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Translates the paging of doc/api.yml into the paging of Spring Data.
 *
 * <p>The API numbers pages from 1, Spring Data from 0. Keeping that single subtraction in one place
 * is what prevents an off-by-one from appearing in one endpoint and not another.
 */
public final class Pagination {

  public static final int MAX_PAGE_SIZE = 500;

  private Pagination() {}

  public static PageRequest toPageRequest(int page, int pageSize, Sort sort) {
    if (page < 1) {
      throw new BadRequestException("Page must be at least 1 but was " + page);
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new BadRequestException(
          "Page size must be between 1 and " + MAX_PAGE_SIZE + " but was " + pageSize);
    }
    return PageRequest.of(page - 1, pageSize, sort);
  }
}
