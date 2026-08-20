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

  @Test
  void a_transcript_pdf_survives_a_round_trip_through_s3() throws Exception {
    var pdf = transcriptPdf();
    assertEquals(
        "%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII), "fixture must be a PDF");
    var key = "transcripts/" + UUID.randomUUID() + ".pdf";
    var source = Files.createTempFile("transcript", ".pdf");
    Files.write(source, pdf);

    var hash = bucketComponent.upload(source.toFile(), key);
    assertTrue(hash != null, "upload must report a hash");

    try (var s3 =
        s3Client(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())),
            Region.of(localstack.getRegion()),
            localstack.getEndpoint())) {
      var head = s3.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
      assertEquals(pdf.length, head.contentLength().intValue());
    }

    var downloaded = bucketComponent.download(key);
    assertArrayEquals(pdf, Files.readAllBytes(downloaded.toPath()));
    assertEquals(
        "%PDF-",
        new String(Files.readAllBytes(downloaded.toPath()), 0, 5, StandardCharsets.US_ASCII));

    var presigned = bucketComponent.presign(key, Duration.ofMinutes(10));
    assertTrue(presigned.toString().contains(key), "presigned url was " + presigned);
    assertTrue(presigned.toString().contains("X-Amz-Signature"), "presigned url was " + presigned);

    Files.deleteIfExists(source);
    Files.deleteIfExists(downloaded.toPath());
  }

  @Test
  void a_key_that_was_never_uploaded_is_absent() {
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
