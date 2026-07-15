package com.realdev.readle;

import static org.assertj.core.api.Assertions.assertThat;

import com.realdev.readle.domain.auth.service.OAuthStateService;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = ReadleApplication.class, properties = "spring.cloud.aws.s3.enabled=false")
@ActiveProfiles("test")
class OAuthStateConcurrencyIntegrationTest {

  @Autowired private DataSource dataSource;

  @Autowired private OAuthStateService oauthStateService;

  @Test
  void mysqlSerializesConcurrentConsumesOfTheSameRawState() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("mysql");
    }

    OAuthStateService.OAuthStart start = oauthStateService.create(OAuthProvider.GOOGLE, "/");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<ConsumeAttempt>> attempts =
          List.of(
              executor.submit(() -> consumeAfterRelease(ready, release, start.state())),
              executor.submit(() -> consumeAfterRelease(ready, release, start.state())));

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      release.countDown();

      List<ConsumeAttempt> results = attempts.stream().map(this::getWithinTimeout).toList();

      assertThat(results).filteredOn(ConsumeAttempt::succeeded).hasSize(1);
      assertThat(results)
          .filteredOn(result -> result.failure() != null)
          .singleElement()
          .satisfies(
              result ->
                  assertThat(result.failure().getErrorCode())
                      .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED));
    } finally {
      executor.shutdownNow();
    }
  }

  private ConsumeAttempt consumeAfterRelease(
      CountDownLatch ready, CountDownLatch release, String rawState) throws InterruptedException {
    ready.countDown();
    if (!release.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent OAuth state consumers were not released");
    }
    try {
      oauthStateService.consume(OAuthProvider.GOOGLE, rawState);
      return new ConsumeAttempt(true, null);
    } catch (CustomException exception) {
      return new ConsumeAttempt(false, exception);
    }
  }

  private ConsumeAttempt getWithinTimeout(Future<ConsumeAttempt> attempt) {
    try {
      return attempt.get(10, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new AssertionError("Concurrent OAuth state consume did not complete", exception);
    }
  }

  private record ConsumeAttempt(boolean succeeded, CustomException failure) {}
}
