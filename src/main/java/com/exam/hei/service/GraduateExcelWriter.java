package com.exam.hei.service;

import com.exam.hei.model.Graduate;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Renders a graduate list as an XLSX workbook. Kept apart from {@link GraduateService}: producing
 * bytes for a specific file format is a rendering concern, not a business one.
 */
@Component
public class GraduateExcelWriter {

  private static final List<String> HEADER =
      List.of("Rank", "STD", "Last name", "First name", "General average");

  public byte[] write(List<Graduate> graduates) {
    try (var workbook = new XSSFWorkbook();
        var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Graduates");
      writeHeader(sheet.createRow(0));
      for (var i = 0; i < graduates.size(); i++) {
        writeRow(sheet.createRow(i + 1), graduates.get(i));
      }
      workbook.write(out);
      return out.toByteArray();
    } catch (java.io.IOException e) {
      // An in-memory workbook writing to a ByteArrayOutputStream has no real failure mode; the
      // checked signature is POI's, not a case this API can meaningfully recover from.
      throw new UncheckedIOException(e);
    }
  }

  private void writeHeader(Row row) {
    for (var i = 0; i < HEADER.size(); i++) {
      row.createCell(i).setCellValue(HEADER.get(i));
    }
  }

  /** Rank and average as numeric cells, so a spreadsheet can sort or format them as numbers. */
  private void writeRow(Row row, Graduate graduate) {
    row.createCell(0).setCellValue(graduate.rank());
    row.createCell(1).setCellValue(graduate.std());
    row.createCell(2).setCellValue(graduate.lastName());
    row.createCell(3).setCellValue(graduate.firstName());
    row.createCell(4).setCellValue(graduate.generalAverage().doubleValue());
  }
}
