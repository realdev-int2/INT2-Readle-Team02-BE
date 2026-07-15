package com.realdev.readle.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizAiGradingServiceTest {

  @InjectMocks private QuizAiGradingService quizAiGradingService;

  @Mock private ClaudeClient claudeClient;
  @Mock private PromptLoader promptLoader;
  @Mock private ObjectMapper objectMapper;

  private QuizQuestion question;

  @BeforeEach
  void setUp() {
    // We inject a real ObjectMapper to test Jackson annotations (@JsonIgnoreProperties etc)
    objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    ReflectionTestUtils.setField(quizAiGradingService, "objectMapper", objectMapper);

    // Use a real executor for timeout tests
    ReflectionTestUtils.setField(
        quizAiGradingService,
        "gradingExecutor",
        java.util.concurrent.Executors.newSingleThreadExecutor());

    question = mock(QuizQuestion.class);
    ReflectionTestUtils.setField(question, "id", 10L);
    given(question.getQuestionText()).willReturn("스프링 빈의 스코프 중 싱글톤은 무엇인가요?");
    given(question.getCorrectAnswer()).willReturn("하나의 인스턴스만 생성되어 공유됨");
  }

  @Test
  @DisplayName("AI 채점 정상 응답 - 정답인 경우")
  void gradeAnswerAsync_Success() throws Exception {
    // given
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");
    given(claudeClient.getGradingGeneratedText(any(), any()))
        .willReturn("{\"isCorrect\": true, \"aiFeedback\": null}");

    // No need to stub ObjectMapper because it's real

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "한번만 생성됩니다", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isTrue();
    assertThat(result.aiFeedback()).isNull();
    assertThat(result.submittedAnswer()).isEqualTo("한번만 생성됩니다");
    verify(claudeClient, times(1)).getGradingGeneratedText(any(), any());
  }

  @Test
  @DisplayName("JSON 파싱 에러 시 1회 재시도 후 성공")
  void gradeAnswerAsync_Retry_Success() throws Exception {
    // given
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 첫번째 호출은 파싱 실패하게 만들응답
    // 두번째 호출은 정상 응답
    given(claudeClient.getGradingGeneratedText(any(), any()))
        .willReturn("invalid_json")
        .willReturn("{\"isCorrect\": false, \"aiFeedback\": \"틀림\"}");

    // No need to stub ObjectMapper

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isFalse();
    assertThat(result.aiFeedback()).isEqualTo("틀림");
    // getGradingGeneratedText가 재시도 포함 총 2회 호출되어야 함
    verify(claudeClient, times(2)).getGradingGeneratedText(any(), any());
  }

  @Test
  @DisplayName("재시도까지 실패 시 Fallback 결과 반환")
  void gradeAnswerAsync_Fallback() throws Exception {
    // given
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 계속 실패
    given(claudeClient.getGradingGeneratedText(any(), any()))
        .willThrow(new RuntimeException("API Error"));

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isFalse(); // Fallback 처리됨
    assertThat(result.aiFeedback()).contains("연결이 원활하지 않아");
    verify(claudeClient, times(2)).getGradingGeneratedText(any(), any()); // 최초 + 1회 재시도 = 2번 호출
  }

  @Test
  @DisplayName("isCorrect 누락 시 1회 재시도 후 Fallback 결과 반환")
  void gradeAnswerAsync_MissingIsCorrect_Fallback() throws Exception {
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 두 번 모두 isCorrect 필드가 누락된 JSON 반환
    given(claudeClient.getGradingGeneratedText(any(), any()))
        .willReturn("{\"aiFeedback\": \"어쩌구저쩌구\"}");

    // No need to stub ObjectMapper

    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    assertThat(result.isCorrect()).isFalse(); // Fallback
    assertThat(result.aiFeedback()).contains("연결이 원활하지 않아");
    verify(claudeClient, times(2)).getGradingGeneratedText(any(), any());
  }

  @Test
  @DisplayName("AI 응답이 3초를 초과할 경우 타임아웃 발생 및 Fallback 처리")
  void gradeAnswerAsync_Timeout() throws Exception {
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    given(claudeClient.getGradingGeneratedText(any(), any()))
        .willAnswer(
            invocation -> {
              Thread.sleep(4000); // 3초 타임아웃 초과 시뮬레이션
              return "{\"isCorrect\": true, \"aiFeedback\": \"정답\"}";
            });

    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "지연 응답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    assertThat(result.isCorrect()).isFalse(); // 타임아웃으로 인한 Fallback
    assertThat(result.aiFeedback()).contains("연결이 원활하지 않아");

    // 첫 호출에서 타임아웃나면 재시도 1번 더 하므로 2번 호출됨 (재시도도 타임아웃 남)
    verify(claudeClient, times(2)).getGradingGeneratedText(any(), any());
  }
}
