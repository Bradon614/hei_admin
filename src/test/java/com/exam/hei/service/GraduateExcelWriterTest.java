package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.exam.hei.model.Graduate;
import com.exam.hei.repository.model.Track;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class GraduateExcelWriterTest {
  private final GraduateExcelWriter subject = new GraduateExcelWriter();

  private static Graduate graduate(
      int rank, String std, String lastName, String firstName, String average) {
    return new Graduate(
        rank,
        std,
        lastName,
        firstName,
        new BigDecimal(average),
        Track.builder().code("EL").build());
  }

  private Row readRow(byte[] xlsx, int rowIndex) throws IOException {
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      return workbook.getSheetAt(0).getRow(rowIndex);
    }
  }

  @Test
  void the_first_row_is_the_header() throws IOException {
    var row = readRow(subject.write(List.of()), 0);

    assertEquals("Rank", row.getCell(0).getStringCellValue());
    assertEquals("STD", row.getCell(1).getStringCellValue());
    assertEquals("Last name", row.getCell(2).getStringCellValue());
    assertEquals("First name", row.getCell(3).getStringCellValue());
    assertEquals("General average", row.getCell(4).getStringCellValue());
  }

  @Test
  void an_empty_list_writes_only_the_header() throws IOException {
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(subject.write(List.of())))) {
      assertEquals(0, workbook.getSheetAt(0).getLastRowNum());
    }
  }

  @Test
  void one_row_is_written_per_graduate_with_matching_values() throws IOException {
    var xlsx = subject.write(List.of(graduate(1, "STD22045", "Rakoto", "Jean", "15.42")));

    var row = readRow(xlsx, 1);

    assertEquals(1.0, row.getCell(0).getNumericCellValue());
    assertEquals("STD22045", row.getCell(1).getStringCellValue());
    assertEquals("Rakoto", row.getCell(2).getStringCellValue());
    assertEquals("Jean", row.getCell(3).getStringCellValue());
    assertEquals(15.42, row.getCell(4).getNumericCellValue());
  }

  @Test
  void several_graduates_are_written_in_order() throws IOException {
    var xlsx =
        subject.write(
            List.of(
                graduate(1, "STD1", "Rakoto", "Jean", "16.00"),
                graduate(2, "STD2", "Randria", "Aina", "12.00")));

    assertEquals("STD1", readRow(xlsx, 1).getCell(1).getStringCellValue());
    assertEquals("STD2", readRow(xlsx, 2).getCell(1).getStringCellValue());
  }
}
