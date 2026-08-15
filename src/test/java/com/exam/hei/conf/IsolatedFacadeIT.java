package com.exam.hei.conf;

import static java.lang.Runtime.getRuntime;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Same wiring as {@link FacadeIT}, but on a database of its own rather than the one every other
 * integration test shares.
 *
 * <p>For the rare test whose subject is the state of an empty schema. {@code AdminBootstrapper}
 * only acts when no administrator exists at all, and the shared database holds hundreds of them by
 * the time any assertion runs — so the behaviour that matters on a fresh deployment is invisible
 * there, and only there.
 *
 * <p>The container is shared between subclasses of this class, not created per class. That is fine
 * while a single test needs it; a second one wanting a pristine schema would have to reckon with
 * the first.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class IsolatedFacadeIT {

  private static final PostgresConf POSTGRES_CONF = new PostgresConf();

  @BeforeAll
  static void beforeAll() {
    POSTGRES_CONF.start();
    getRuntime().addShutdownHook(new Thread(POSTGRES_CONF::stop));
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    POSTGRES_CONF.configureProperties(registry);
    new EventConf().configureProperties(registry);
    new BucketConf().configureProperties(registry);
    new EmailConf().configureProperties(registry);
    new EnvConf().configureProperties(registry);
  }
}
