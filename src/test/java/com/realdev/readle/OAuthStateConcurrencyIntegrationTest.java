package com.realdev.readle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

import com.realdev.readle.domain.auth.repository.OAuthAuthorizationStateRepository;
import com.realdev.readle.domain.auth.service.OAuthStateService;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = ReadleApplication.class, properties = "spring.cloud.aws.s3.enabled=false")
@ActiveProfiles("test")
class OAuthStateConcurrencyIntegrationTest {

  @Autowired private DataSource dataSource;

  @Autowired private OAuthStateService oauthStateService;

  @Autowired private SecurityProperties securityProperties;

  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoSpyBean private OAuthAuthorizationStateRepository stateRepository;

  @Test
  void mysqlBlocksSecondConsumeUntilFirstLockedConsumeCompletes() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("mysql");
    }

    LockBarrier lockBarrier = new LockBarrier();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Answer<?> realRepository =
          mockingDetails(stateRepository).getMockCreationSettings().getDefaultAnswer();
      doAnswer(
              invocation -> {
                Optional<?> state = (Optional<?>) realRepository.answer(invocation);
                if (state.isPresent()) {
                  lockBarrier.afterLockedStateFound();
                }
                return state;
              })
          .when(stateRepository)
          .findByStateHashAndOauthProvider(any(), any());

      long stateCountBefore = stateRepository.count();
      OAuthStateService.OAuthStart start = oauthStateService.create(OAuthProvider.GOOGLE, "/");

      Future<ConsumeAttempt> first = executor.submit(() -> consume(start.state()));
      assertThat(lockBarrier.awaitLockedStateFound()).isTrue();

      Future<ConsumeAttempt> second = executor.submit(() -> consume(start.state()));
      assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);

      lockBarrier.release();

      List<ConsumeAttempt> results =
          List.of(first, second).stream().map(this::getWithinTimeout).toList();

      assertThat(results).filteredOn(ConsumeAttempt::succeeded).hasSize(1);
      assertThat(results)
          .filteredOn(
              result ->
                  result.failure() != null
                      && result.failure().getErrorCode()
                          == GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED)
          .hasSize(1);
      assertThat(lockBarrier.invocationCount()).isEqualTo(1);
      assertThat(stateRepository.count()).isEqualTo(stateCountBefore);
    } finally {
      lockBarrier.release();
      executor.shutdownNow();
    }
  }

  private ConsumeAttempt consume(String rawState) {
    try {
      OAuthStateService stateService =
          new OAuthStateService(stateRepository, securityProperties, transactionManager);
      stateService.consume(OAuthProvider.GOOGLE, rawState);
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

  static class LockBarrier {

    private final AtomicInteger invocationCount = new AtomicInteger();
    private final CountDownLatch locked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    public void afterLockedStateFound() {
      invocationCount.incrementAndGet();
      locked.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Locked OAuth state consume was not released");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Locked OAuth state consume was interrupted", exception);
      }
    }

    boolean awaitLockedStateFound() throws InterruptedException {
      return locked.await(5, TimeUnit.SECONDS);
    }

    void release() {
      release.countDown();
    }

    int invocationCount() {
      return invocationCount.get();
    }
  }
}
