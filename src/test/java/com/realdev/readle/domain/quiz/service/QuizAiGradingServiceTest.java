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
import com.realdev.readle.domain.quiz.dto.ClaudeGradingResponseDto;
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
    ReflectionTestUtils.setField(
        quizAiGradingService, "gradingExecutor", (java.util.concurrent.Executor) Runnable::run);

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
    given(claudeClient.getGeneratedText(any(), any()))
        .willReturn("{\"isCorrect\": true, \"aiFeedback\": null}");

    ClaudeGradingResponseDto mockDto = new ClaudeGradingResponseDto(true, null);
    given(
            objectMapper.readValue(
                "{\"isCorrect\": true, \"aiFeedback\": null}", ClaudeGradingResponseDto.class))
        .willReturn(mockDto);

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "한번만 생성됩니다", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isTrue();
    assertThat(result.aiFeedback()).isNull();
    assertThat(result.submittedAnswer()).isEqualTo("한번만 생성됩니다");
    verify(claudeClient, times(1)).getGeneratedText(any(), any());
  }

  @Test
  @DisplayName("JSON 파싱 에러 시 1회 재시도 후 성공")
  void gradeAnswerAsync_Retry_Success() throws Exception {
    // given
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 첫번째 호출은 파싱 실패하게 만들응답
    // 두번째 호출은 정상 응답
    given(claudeClient.getGeneratedText(any(), any()))
        .willReturn("invalid_json")
        .willReturn("{\"isCorrect\": false, \"aiFeedback\": \"틀림\"}");

    // 첫번째 ObjectMapper 호출에서 예외 발생
    given(objectMapper.readValue("invalid_json", ClaudeGradingResponseDto.class))
        .willThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Parsing error"));

    ClaudeGradingResponseDto mockDto = new ClaudeGradingResponseDto(false, "틀림");
    given(
            objectMapper.readValue(
                "{\"isCorrect\": false, \"aiFeedback\": \"틀림\"}", ClaudeGradingResponseDto.class))
        .willReturn(mockDto);

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isFalse();
    assertThat(result.aiFeedback()).isEqualTo("틀림");
    // getGeneratedText가 재시도 포함 총 2회 호출되어야 함
    verify(claudeClient, times(2)).getGeneratedText(any(), any());
  }

  @Test
  @DisplayName("재시도까지 실패 시 Fallback 결과 반환")
  void gradeAnswerAsync_Fallback() throws Exception {
    // given
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 계속 실패
    given(claudeClient.getGeneratedText(any(), any())).willThrow(new RuntimeException("API Error"));

    // when
    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    // then
    assertThat(result.isCorrect()).isFalse(); // Fallback 처리됨
    assertThat(result.aiFeedback()).contains("연결이 원활하지 않아");
    verify(claudeClient, times(2)).getGeneratedText(any(), any()); // 최초 + 1회 재시도 = 2번 호출
  }

  @Test
  @DisplayName("isCorrect 누락 시 1회 재시도 후 Fallback 결과 반환")
  void gradeAnswerAsync_MissingIsCorrect_Fallback() throws Exception {
    given(promptLoader.loadPrompt(eq("quiz-grading.txt"), anyMap())).willReturn("system_prompt");

    // 두 번 모두 isCorrect 필드가 누락된 JSON 반환
    given(claudeClient.getGeneratedText(any(), any())).willReturn("{\"aiFeedback\": \"어쩌구저쩌구\"}");

    ClaudeGradingResponseDto mockDto = new ClaudeGradingResponseDto(null, "어쩌구저쩌구");
    given(objectMapper.readValue("{\"aiFeedback\": \"어쩌구저쩌구\"}", ClaudeGradingResponseDto.class))
        .willReturn(mockDto);

    CompletableFuture<QuizAiGradingService.AiEvaluationResult> future =
        quizAiGradingService.gradeAnswerAsync(question, "오답", "본문 텍스트");
    QuizAiGradingService.AiEvaluationResult result = future.join();

    assertThat(result.isCorrect()).isFalse(); // Fallback
    assertThat(result.aiFeedback()).contains("연결이 원활하지 않아");
    verify(claudeClient, times(2)).getGeneratedText(any(), any());
  }
}
