package com.exam.hei.endpoint.rest.controller;

import com.exam.hei.endpoint.rest.mapper.GraduateMapper;
import com.exam.hei.endpoint.rest.model.Graduate;
import com.exam.hei.service.GraduateExcelWriter;
import com.exam.hei.service.GraduateService;
import com.exam.hei.service.PromotionService;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Role restrictions live in SecurityConf. */
@RestController
@AllArgsConstructor
public class GraduateController {

  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final GraduateService graduateService;
  private final GraduateMapper graduateMapper;
  private final GraduateExcelWriter graduateExcelWriter;
  private final PromotionService promotionService;

  @GetMapping("/promotions/{promotionId}/graduates")
  public List<Graduate> getPromotionGraduates(@PathVariable UUID promotionId) {
    return graduateService.graduatesOf(promotionId).stream().map(graduateMapper::toRest).toList();
  }

  @GetMapping("/promotions/{promotionId}/graduates/excel")
  public ResponseEntity<byte[]> getPromotionGraduatesExcel(@PathVariable UUID promotionId) {
    // Resolved first so a 404 on an unknown promotion never depends on evaluation order between
    // this and the graduate computation below.
    var promotion = promotionService.findById(promotionId);
    var bytes = graduateExcelWriter.write(graduateService.graduatesOf(promotionId));

    return ResponseEntity.ok()
        .contentType(XLSX)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"graduates-" + promotion.getRef() + ".xlsx\"")
        .body(bytes);
  }
}
