package com.exam.hei.file.bucket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.exam.hei.model.CourseResult;
import com.exam.hei.model.SemesterResult;
import com.exam.hei.model.SemesterResultStatus;
import com.exam.hei.model.StudentResult;
import com.exam.hei.repository.model.Course;
import com.exam.hei.repository.model.Promotion;
import com.exam.hei.repository.model.Semester;
import com.exam.hei.repository.model.SemesterRef;
import com.exam.hei.repository.model.Student;
import com.exam.hei.service.TranscriptPdfGenerator;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * The real {@link BucketComponent}, against a real S3 implementation, all the way from a real PDF.
 *
 * <p>Nothing here can reach the AWS account. The credentials are the throwaway pair the container
 * hands out, injected through a {@link StaticCredentialsProvider} so the default credential chain —
 * profiles, environment, instance roles — is never consulted; the endpoint points at the container;
 * the bucket is created inside it. One small object is uploaded, once.
 *
 * <p>{@code BucketConf} is {@code @PojaGenerated} and offers no endpoint override, so it is
 * subclassed here, in test scope only, to hand {@link BucketComponent} clients aimed at the
 * container. No generated file is modified.
 */
class BucketComponentLocalStackIT {

  private static final String BUCKET = "hei-transcripts-test";

  private static LocalStackContainer localstack;
  private static BucketComponent bucketComponent;

  @BeforeAll
  static void startLocalStack() {
    localstack = new LocalStackContainer("localstack/localstack:3.4").withServices("s3");
    localstack.start();

    var credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
    var region = Region.of(localstack.getRegion());
    var endpoint = localstack.getEndpoint();

    createBucket(credentials, region, endpoint);
    bucketComponent = new BucketComponent(localStackConf(credentials, region, endpoint));
  }

  @AfterAll
  static void stopLocalStack() {
    if (localstack != null) {
      localstack.stop();
    }
  }

  private static void createBucket(
      StaticCredentialsProvider credentials, Region region, URI endpoint) {
    try (var s3 = s3Client(credentials, region, endpoint)) {
      s3.createBucket(request -> request.bucket(BUCKET));
    }
  }

  private static S3Client s3Client(
      StaticCredentialsProvider credentials, Region region, URI endpoint) {
    return S3Client.builder()
        .credentialsProvider(credentials)
        .region(region)
        .endpointOverride(endpoint)
        .forcePathStyle(true)
        .build();
  }

  /**
   * The generated configuration builds its clients for the real eu-west-3 endpoint with no way to
   * point them elsewhere. Its getters are overridable, which is enough: the component under test
   * still is the real one, it simply receives clients aimed at the container.
   *
   * <p>The async client is the plain one rather than the CRT builder the generated class uses: that
   * is the only concession, and it is a transport detail — the S3 protocol exercised below is the
   * same.
   */
  private static BucketConf localStackConf(
      StaticCredentialsProvider credentials, Region region, URI endpoint) {
    var asyncClient =
        S3AsyncClient.builder()
            .credentialsProvider(credentials)
            .region(region)
            .endpointOverride(endpoint)
            .forcePathStyle(true)
            .build();
    var transferManager = S3TransferManager.builder().s3Client(asyncClient).build();
    var presigner =
        S3Presigner.builder()
            .credentialsProvider(credentials)
            .region(region)
            .endpointOverride(endpoint)
            .build();
    var syncClient = s3Client(credentials, region, endpoint);

    return new BucketConf(region.id(), BUCKET) {
      @Override
      public S3TransferManager getS3TransferManager() {
        return transferManager;
      }

      @Override
      public S3Presigner getS3Presigner() {
        return presigner;
      }

      @Override
      public S3Client getS3Client() {
        return syncClient;
      }

      @Override
      public String getBucketName() {
        return BUCKET;
      }
    };
  }

  // --- the fixture: a real PDF, from the real generator ---------------------------

  private static byte[] transcriptPdf() {
    var student =
        Student.builder()
            .id(UUID.randomUUID())
            .ref("STD22045")
            .firstName("Jean")
            .lastName("Rakoto")
            .email("jean.rakoto@hei.school")
            .promotion(Promotion.builder().ref("K22").name("Promotion K 2022").build())
            .build();
    var course =
        Course.builder()
            .id(UUID.randomUUID())
            .ref("PROG1")
            .title("Programming")
            .credits(30)
            .build();
    var semesterResult =
        new SemesterResult(
            Semester.builder()
                .id(UUID.randomUUID())
                .ref(SemesterRef.S1)
                .requiredCredits(30)
                .build(),
            SemesterResultStatus.EVALUATED,
            null,
            30,
            30,
            true,
            List.of(new CourseResult(course, new BigDecimal("14.00"), true, 30)));
    var result =
        new StudentResult(
            student, List.of(semesterResult), 30, new BigDecimal("14.00"), false, List.of());

    return new TranscriptPdfGenerator().generate(result, null);
  }

  // --- the flow -------------------------------------------------------------------

  @Test
  void a_transcript_pdf_survives_a_round_trip_through_s3() throws Exception {
    var pdf = transcriptPdf();
    assertEquals(
        "%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII), "fixture must be a PDF");
    var key = "transcripts/" + UUID.randomUUID() + ".pdf";
    var source = Files.createTempFile("transcript", ".pdf");
    Files.write(source, pdf);

    // upload
    var hash = bucketComponent.upload(source.toFile(), key);
    assertTrue(hash != null, "upload must report a hash");

    // the object really is in the bucket
    try (var s3 =
        s3Client(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())),
            Region.of(localstack.getRegion()),
            localstack.getEndpoint())) {
      var head = s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
      assertEquals(pdf.length, head.contentLength().intValue());
    }

    // download returns exactly what went up
    var downloaded = bucketComponent.download(key);
    assertArrayEquals(pdf, Files.readAllBytes(downloaded.toPath()));
    assertEquals(
        "%PDF-",
        new String(Files.readAllBytes(downloaded.toPath()), 0, 5, StandardCharsets.US_ASCII));

    // and a presigned link can be handed out
    var presigned = bucketComponent.presign(key, Duration.ofMinutes(10));
    assertTrue(presigned.toString().contains(key), "presigned url was " + presigned);
    assertTrue(presigned.toString().contains("X-Amz-Signature"), "presigned url was " + presigned);

    Files.deleteIfExists(source);
    Files.deleteIfExists(downloaded.toPath());
  }

  @Test
  void a_key_that_was_never_uploaded_is_absent() {
    // Proves the previous test's assertions mean something: the bucket is not answering yes to
    // everything.
    try (var s3 =
        s3Client(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())),
            Region.of(localstack.getRegion()),
            localstack.getEndpoint())) {
      org.junit.jupiter.api.Assertions.assertThrows(
          NoSuchKeyException.class,
          () ->
              s3.headObject(
                  HeadObjectRequest.builder().bucket(BUCKET).key("transcripts/never.pdf").build()));
    }
  }

  @Test
  void the_component_reports_the_bucket_it_was_configured_with() {
    assertEquals(BUCKET, bucketComponent.getBucketName());
  }
}
