package com.exam.hei.conf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TestRefsTest {
  @Test
  void a_reference_fits_the_five_characters_the_column_allows() {
    assertEquals(5, TestRefs.promotionRef().length());
  }

  @Test
  void ten_thousand_references_never_repeat() {
    var seen = new HashSet<String>();

    for (var i = 0; i < 10_000; i++) {
      assertTrue(seen.add(TestRefs.promotionRef()), "duplicate after " + i);
    }
  }

  @Test
  void references_stay_unique_when_handed_out_from_several_threads() throws Exception {
    List<Callable<String>> calls =
        IntStream.range(0, 2_000).<Callable<String>>mapToObj(i -> TestRefs::promotionRef).toList();

    try (var pool = Executors.newFixedThreadPool(16)) {
      var refs = new HashSet<String>();
      for (var future : pool.invokeAll(calls)) {
        assertTrue(refs.add(future.get()), "duplicate under concurrency");
      }
      assertEquals(2_000, refs.size());
    }
  }

  @Test
  void a_reference_is_alphanumeric_so_it_reads_like_a_cohort_code() {
    assertTrue(TestRefs.promotionRef().matches("[0-9A-Z]{5}"), TestRefs.promotionRef());
  }
}
