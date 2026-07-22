package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ErrorCode;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.config.ClaudeTestConfig;
import com.realdev.readle.global.exception.CustomException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ClaudeTestConfig.class)
class ContentServiceConcurrencyTest {

  @Autowired private ContentService contentService;
  @Autowired private ContentRepository contentRepository;
  @Autowired private ContentValidationRepository contentValidationRepository;
  @Autowired private MemberRepository memberRepository;

  private Member testMember;
  private Content testContent;

  @BeforeEach
  void setUp() {
    testMember =
        memberRepository.save(
            Member.create(
                OAuthProvider.KAKAO,
                "12345",
                "test@example.com",
                "testUser",
                "http://example.com/profile.jpg"));

    testContent = contentRepository.save(Content.fromText(testMember, "테스트 제목", "테스트 텍스트"));

    // FAILED 이력 생성
    contentValidationRepository.save(
        ContentValidation.builder()
            .content(testContent)
            .validationMethod(ValidationMethod.AI)
            .status(ValidationStatus.FAILED)
            .errorCode(ErrorCode.AI_SERVICE_ERROR)
            .build());
  }

  @AfterEach
  void tearDown() {
    contentValidationRepository.deleteAllInBatch();
    contentRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("재시도(retryValidation) 동시성 제어 - 여러 요청이 동시에 들어와도 단 1번만 PENDING이 생성되어야 한다")
  void retryValidation_concurrencyControl() throws InterruptedException {
    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger blockedCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executorService.submit(
          () -> {
            try {
              startLatch.await();
              contentService.retryValidation(testContent.getId(), testMember.getUuid());
              successCount.incrementAndGet();
            } catch (CustomException e) {
              if (e.getErrorCode() == ContentErrorCode.VALIDATION_ALREADY_RUNNING
                  || e.getErrorCode() == ContentErrorCode.NOT_RETRYABLE) {
                blockedCount.incrementAndGet();
              } else {
                e.printStackTrace();
              }
            } catch (Exception e) {
              System.out.println(
                  "Unexpected exception: " + e.getClass().getName() + " - " + e.getMessage());
              e.printStackTrace();
            } finally {
              latch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean completed = latch.await(10, TimeUnit.SECONDS);
    assertThat(completed).as("Test timed out waiting for threads to complete").isTrue();
    executorService.shutdown();

    System.out.println("successCount: " + successCount.get());
    System.out.println("blockedCount: " + blockedCount.get());

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(blockedCount.get()).isEqualTo(threadCount - 1);
  }
}
