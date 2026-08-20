package com.exam.hei.service;

import com.exam.hei.model.Graduate;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class GraduateExcelWriter {
  private static final List<String> HEADER =
      List.of("Rank", "STD", "Last name", "First name", "General average", "Track");

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
      throw new UncheckedIOException(e);
    }
  }

  private void writeHeader(Row row) {
    for (var i = 0; i < HEADER.size(); i++) {
      row.createCell(i).setCellValue(HEADER.get(i));
    }
  }

  private void writeRow(Row row, Graduate graduate) {
    row.createCell(0).setCellValue(graduate.rank());
    row.createCell(1).setCellValue(graduate.std());
    row.createCell(2).setCellValue(graduate.lastName());
    row.createCell(3).setCellValue(graduate.firstName());
    row.createCell(4).setCellValue(graduate.generalAverage().doubleValue());
    row.createCell(5).setCellValue(graduate.track() == null ? "" : graduate.track().getCode());
  }
}
