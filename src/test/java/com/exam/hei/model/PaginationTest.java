package com.exam.hei.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.exam.hei.model.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PaginationTest {
  private static final Sort ANY_SORT = Sort.by("ref");

  @Test
  void the_first_page_of_the_api_is_the_first_page_of_spring_data() {
    assertEquals(0, Pagination.toPageRequest(1, 50, ANY_SORT).getPageNumber());
    assertEquals(1, Pagination.toPageRequest(2, 50, ANY_SORT).getPageNumber());
  }

  @Test
  void the_requested_page_size_is_carried_over() {
    assertEquals(25, Pagination.toPageRequest(1, 25, ANY_SORT).getPageSize());
  }

  @Test
  void a_page_below_one_is_rejected() {
    assertThrows(BadRequestException.class, () -> Pagination.toPageRequest(0, 50, ANY_SORT));
    assertThrows(BadRequestException.class, () -> Pagination.toPageRequest(-1, 50, ANY_SORT));
  }

  @Test
  void a_page_size_outside_its_bounds_is_rejected() {
    assertThrows(BadRequestException.class, () -> Pagination.toPageRequest(1, 0, ANY_SORT));
    assertThrows(
        BadRequestException.class,
        () -> Pagination.toPageRequest(1, Pagination.MAX_PAGE_SIZE + 1, ANY_SORT));
  }

  @Test
  void the_maximum_page_size_is_accepted() {
    assertEquals(
        Pagination.MAX_PAGE_SIZE,
        Pagination.toPageRequest(1, Pagination.MAX_PAGE_SIZE, ANY_SORT).getPageSize());
  }
}
