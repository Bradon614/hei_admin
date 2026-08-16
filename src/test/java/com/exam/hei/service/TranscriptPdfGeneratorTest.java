package com.exam.hei.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.model.CourseResult;
import com.exam.hei.model.GraduationBlocker;
import com.exam.hei.model.GraduationBlockerCode;
import com.exam.hei.model.SemesterResult;
import com.exam.hei.model.SemesterResultStatus;
import com.exam.hei.model.StudentResult;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.repository.model.Track;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TranscriptPdfGeneratorTest {
  private final TranscriptPdfGenerator subject = new TranscriptPdfGenerator();

  private static final Track EL = Track.builder().id(UUID.randomUUID()).code("EL").build();

  private static Student student() {
    return Student.builder()
        .id(UUID.randomUUID())
        .ref("STD22045")
        .firstName("Jean")
        .lastName("Rakoto")
        .email("jean.rakoto@hei.school")
        .promotion(Promotion.builder().ref("K22").name("Promotion K 2022").build())
        .build();
  }

  private static Semester semester(SemesterRef ref) {
    return Semester.builder().id(UUID.randomUUID()).ref(ref).requiredCredits(30).build();
  }

  private static CourseResult courseResult(String ref, String title, int credits, String grade) {
    var course =
        Course.builder().id(UUID.randomUUID()).ref(ref).title(title).credits(credits).build();
    return grade == null
        ? new CourseResult(course, null, false, 0)
        : new CourseResult(course, new BigDecimal(grade), true, credits);
  }

  private static SemesterResult evaluated(SemesterRef ref, Track track, CourseResult... courses) {
    return new SemesterResult(
        semester(ref), SemesterResultStatus.EVALUATED, track, 30, 30, true, List.of(courses));
  }

  private static SemesterResult notSelected(SemesterRef ref) {
    return new SemesterResult(
        semester(ref), SemesterResultStatus.TRACK_NOT_SELECTED, null, 0, 30, false, List.of());
  }

  private static StudentResult graduatedResult() {
    return new StudentResult(
        student(),
        List.of(
            evaluated(SemesterRef.S1, null, courseResult("PROG1", "Programming", 30, "14.00")),
            evaluated(SemesterRef.S2, null, courseResult("ALGO2", "Algorithms", 30, "12.00")),
            evaluated(SemesterRef.S3, null, courseResult("MATH3", "Mathematics", 30, "11.00")),
            evaluated(SemesterRef.S4, EL, courseResult("WEB4", "Web development", 30, "16.00")),
            evaluated(
                SemesterRef.S5, EL, courseResult("PROGAV", "Advanced programming", 30, "15.00")),
            evaluated(SemesterRef.S6, EL, courseResult("ARCHI", "Architecture", 30, "13.00"))),
        180,
        new BigDecimal("13.50"),
        true,
        List.of());
  }

  private String textOf(byte[] pdf) throws Exception {
    var reader = new PdfReader(pdf);
    var extractor = new PdfTextExtractor(reader);
    var text = new StringBuilder();
    for (var page = 1; page <= reader.getNumberOfPages(); page++) {
      text.append(extractor.getTextFromPage(page));
    }
    reader.close();
    return text.toString();
  }

  @Test
  void the_output_is_a_pdf() {
    var pdf = subject.generate(graduatedResult(), null);

    assertTrue(pdf.length > 0);
    assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
  }

  @Test
  void the_template_renders_as_well_formed_xml() throws Exception {
    var xhtml = subject.render(graduatedResult(), null);

    javax.xml.parsers.DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new org.xml.sax.InputSource(new java.io.StringReader(xhtml)));
  }

  @Test
  void the_pdf_names_the_student_and_their_reference() throws Exception {
    var text = textOf(subject.generate(graduatedResult(), null));

    assertTrue(text.contains("Jean Rakoto"), "text was " + text);
    assertTrue(text.contains("STD22045"), "text was " + text);
    assertTrue(text.contains("jean.rakoto@hei.school"), "text was " + text);
  }

  @Test
  void the_pdf_lists_the_courses_of_every_semester() throws Exception {
    var text = textOf(subject.generate(graduatedResult(), null));

    for (var ref : SemesterRef.values()) {
      assertTrue(text.contains(ref.name()), ref + " missing from " + text);
    }
    assertTrue(text.contains("Advanced programming"), "text was " + text);
    assertTrue(text.contains("Architecture"), "text was " + text);
  }

  @Test
  void a_full_transcript_states_the_diploma_verdict() throws Exception {
    var text = textOf(subject.generate(graduatedResult(), null));

    assertTrue(text.contains("GRANTED"), "text was " + text);
    assertTrue(text.contains("180"), "text was " + text);
  }

  @Test
  void a_non_graduate_is_told_so_and_why() throws Exception {
    var result =
        new StudentResult(
            student(),
            List.of(
                evaluated(SemesterRef.S1, null, courseResult("PROG1", "Programming", 30, "14.00")),
                notSelected(SemesterRef.S4)),
            30,
            new BigDecimal("14.00"),
            false,
            List.of(
                new GraduationBlocker(
                    GraduationBlockerCode.TRACK_NOT_SELECTED,
                    SemesterRef.S4,
                    "No track selected for S4")));

    var text = textOf(subject.generate(result, null));

    assertTrue(text.contains("NOT GRANTED"), "text was " + text);
    assertTrue(text.contains("No track selected for S4"), "text was " + text);
  }

  @Test
  void a_semester_without_a_track_choice_is_marked_not_evaluable() throws Exception {
    var result =
        new StudentResult(
            student(), List.of(notSelected(SemesterRef.S5)), 0, BigDecimal.ZERO, false, List.of());

    var text = textOf(subject.generate(result, null));

    assertTrue(text.contains("Not evaluable"), "text was " + text);
  }

  @Test
  void a_single_semester_scope_prints_that_semester_only() throws Exception {
    var text = textOf(subject.generate(graduatedResult(), SemesterRef.S5));

    assertTrue(text.contains("Advanced programming"), "S5 course expected, text was " + text);
    assertFalse(text.contains("Architecture"), "S6 course must not appear, text was " + text);
    assertFalse(text.contains("Programming\n"), "S1 course must not appear, text was " + text);
  }

  @Test
  void a_single_semester_scope_carries_no_diploma_verdict() throws Exception {
    var text = textOf(subject.generate(graduatedResult(), SemesterRef.S5));

    assertFalse(text.contains("GRANTED"), "text was " + text);
    assertTrue(text.contains("S5"), "text was " + text);
  }

  @Test
  void a_course_with_no_grade_at_all_is_printed_without_inventing_a_zero() throws Exception {
    var result =
        new StudentResult(
            student(),
            List.of(evaluated(SemesterRef.S1, null, courseResult("PROJ1", "Project", 6, null))),
            0,
            BigDecimal.ZERO,
            false,
            List.of());

    var text = textOf(subject.generate(result, null));

    assertTrue(text.contains("Project"), "text was " + text);
    assertFalse(text.contains("0.00"), "a missing grade must not read as a zero, text was " + text);
  }
}
