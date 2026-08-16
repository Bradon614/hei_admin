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

  String render(StudentResult result, SemesterRef scope) {
    var semesterResults = scopedTo(result.semesterResults(), scope);
    var context = new Context();
    context.setVariable("student", result.student());
    context.setVariable("result", result);
    context.setVariable("semesterResults", semesterResults);

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
