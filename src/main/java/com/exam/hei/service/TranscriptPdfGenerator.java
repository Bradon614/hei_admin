package com.exam.hei.service;

import com.exam.hei.model.SemesterResult;
import com.exam.hei.model.StudentResult;
import com.exam.hei.repository.model.SemesterRef;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

/**
 * Turns a computed {@link StudentResult} into a PDF transcript.
 *
 * <p>Takes an already computed result rather than a student id: the arithmetic belongs to {@code
 * ResultService} and is not restated here, so a transcript can never disagree with what the API
 * reports.
 *
 * <p>Runs its own Thymeleaf engine, deliberately not the Spring-managed one that serves the web
 * pages. Flying Saucer parses its input as XML, so this engine is in {@link TemplateMode#XML}: a
 * template that is not well formed fails loudly at render time instead of producing a document the
 * renderer would choke on later.
 *
 * <p>A {@link SpringTemplateEngine} rather than the plain one: the plain engine evaluates its
 * expressions through OGNL, which is not on this project's classpath, while the Spring flavour uses
 * SpEL, which is. Same engine otherwise, and no dependency added for the sake of a PDF.
 */
@Component
public class TranscriptPdfGenerator {

  private static final String TEMPLATE = "transcript";

  private final SpringTemplateEngine templateEngine;

  public TranscriptPdfGenerator() {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.XML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(true);

    this.templateEngine = new SpringTemplateEngine();
    this.templateEngine.setTemplateResolver(resolver);
  }

  /**
   * @param scope the only semester to print, or null for the whole S1 to S6 curriculum
   */
  public byte[] generate(StudentResult result, SemesterRef scope) {
    var xhtml = render(result, scope);
    var renderer = new ITextRenderer();
    renderer.setDocumentFromString(xhtml);
    renderer.layout();

    try (var out = new ByteArrayOutputStream()) {
      renderer.createPDF(out);
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("The transcript PDF could not be rendered", e);
    }
  }

  /** Visible for testing: asserting on the markup is cheaper than on a rendered page. */
  String render(StudentResult result, SemesterRef scope) {
    var semesterResults = scopedTo(result.semesterResults(), scope);
    var context = new Context();
    context.setVariable("student", result.student());
    context.setVariable("result", result);
    context.setVariable("semesterResults", semesterResults);
    // A single semester carries no diploma verdict: graduating is a statement about all six.
    context.setVariable("full", scope == null);
    context.setVariable("scope", scope == null ? "S1 to S6" : scope.name());
    return templateEngine.process(TEMPLATE, context);
  }

  private List<SemesterResult> scopedTo(List<SemesterResult> semesterResults, SemesterRef scope) {
    return scope == null
        ? semesterResults
        : semesterResults.stream().filter(sr -> sr.semester().getRef() == scope).toList();
  }
}
