package com.exam.hei.service;

import com.exam.hei.model.Graduate;
import com.exam.hei.model.Pagination;
import com.exam.hei.model.StudentResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ranks the graduates of a promotion. Computed dynamically from {@link ResultService}: no {@code
 * graduated} field is ever persisted.
 */
@Service
@AllArgsConstructor
public class GraduateService {

  private final ResultService resultService;
  private final StudentTrackChoiceService trackChoiceService;

  public List<Graduate> graduatesOf(UUID promotionId) {
    // 404 on an unknown promotion comes for free from resultsOfPromotion. The page size is the
    // widest allowed rather than a real page: this endpoint is not paginated by doc/api.yml, and a
    // promotion never approaches that many students.
    var graduated =
        resultService.resultsOfPromotion(promotionId, null, 1, Pagination.MAX_PAGE_SIZE).stream()
            .filter(StudentResult::graduated)
            .sorted(
                Comparator.comparing(StudentResult::generalAverage, Comparator.reverseOrder())
                    .thenComparing(sr -> sr.student().getLastName())
                    .thenComparing(sr -> sr.student().getFirstName()))
            .toList();

    var ranked = new ArrayList<Graduate>(graduated.size());
    BigDecimal previousAverage = null;
    var rank = 0;
    for (var i = 0; i < graduated.size(); i++) {
      var studentResult = graduated.get(i);
      if (previousAverage == null
          || studentResult.generalAverage().compareTo(previousAverage) != 0) {
        rank = i + 1;
      }
      previousAverage = studentResult.generalAverage();
      ranked.add(
          new Graduate(
              rank,
              studentResult.student().getRef(),
              studentResult.student().getLastName(),
              studentResult.student().getFirstName(),
              studentResult.generalAverage(),
              trackChoiceService.exitTrackOf(studentResult.student().getId()).orElse(null)));
    }
    return ranked;
  }
}
