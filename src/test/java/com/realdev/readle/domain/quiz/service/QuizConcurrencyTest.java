package com.realdev.readle.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.global.exception.CustomException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class QuizConcurrencyTest {

  @Autowired private QuizGenerationService quizGenerationService;

  @Autowired private QuizSetRepository quizSetRepository;

  @Autowired private ContentValidationRepository contentValidationRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private com.realdev.readle.global.infrastructure.ai.ClaudeClient claudeClient;

  private ContentValidation validation;

  @BeforeEach
  void setUp() {
    // Use JdbcTemplate to insert dummy data because entities lack builders/setters
    jdbcTemplate.update(
        "INSERT INTO member (uuid, oauth_provider, oauth_id, nickname, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())",
        "test-uuid-conc",
        "KAKAO",
        "test-conc",
        "conc");
    Long memberId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM member", Long.class);

    jdbcTemplate.update(
        "INSERT INTO content (member_id, title, input_type, crawl_status, raw_text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
        memberId,
        "Test Title",
        "TEXT",
        "NOT_APPLICABLE",
        "Public void code() {}");
    Long contentId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM content", Long.class);

    jdbcTemplate.update(
        "INSERT INTO content_validation (content_id, validation_method, status, validation_score, created_at) VALUES (?, ?, ?, ?, NOW())",
        contentId,
        "AI",
        "PASSED",
        90.0);
    Long validationId =
        jdbcTemplate.queryForObject("SELECT MAX(id) FROM content_validation", Long.class);

    validation = contentValidationRepository.findById(validationId).orElseThrow();

    String claudeJsonResponse =
        """
            {
              "tags": ["Test"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "multiple_choice",
                  "question": "Q?",
                  "options": ["A", "B"],
                  "code_snippet": null,
                  "answer": "0"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);
  }

  @Autowired
  private com.realdev.readle.domain.quiz.repository.QuizChoiceRepository quizChoiceRepository;

  @Autowired
  private com.realdev.readle.domain.quiz.repository.QuizQuestionRepository quizQuestionRepository;

  @AfterEach
  void tearDown() {
    quizChoiceRepository.deleteAllInBatch();
    quizQuestionRepository.deleteAllInBatch();
    quizSetRepository.deleteAllInBatch();
    contentValidationRepository.deleteAllInBatch();
    jdbcTemplate.execute("DELETE FROM content_tag");
    jdbcTemplate.execute("DELETE FROM tag");
    jdbcTemplate.execute("DELETE FROM content");
    jdbcTemplate.execute("DELETE FROM member");
  }

  @Test
  @DisplayName("동일한 검증 ID로 동시에 퀴즈 생성을 요청하면 1개만 생성되고 나머지는 예외가 발생한다")
  void concurrencyTest_DuplicateQuizCreation() throws InterruptedException {
    int threadCount = 5;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger();
    AtomicInteger failCount = new AtomicInteger();

    Long validationId = validation.getId();

    for (int i = 0; i < threadCount; i++) {
      executorService.submit(
          () -> {
            try {
              quizGenerationService.createQuizSet(validationId);
              successCount.incrementAndGet();
            } catch (CustomException e) {
              failCount.incrementAndGet();
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();

    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(threadCount - 1);

    long count = quizSetRepository.count();
    assertThat(count).isEqualTo(1);
  }
}
