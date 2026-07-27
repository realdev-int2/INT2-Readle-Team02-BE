package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.realdev.readle.domain.quiz.dto.ClaudeGradingResponseDto;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.ai.ClaudeTemplate;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAiGradingService {

  private static final String QUIZ_GRADING = "quiz_grading";

  private final ClaudeClient claudeClient;
  private final ClaudeTemplate claudeTemplate;
  private final PromptLoader promptLoader;
  private final Executor gradingExecutor;
  private Duration timeoutDuration = Duration.ofSeconds(7);

  public record AiEvaluationResult(
      QuizQuestion question, String submittedAnswer, boolean isCorrect, String aiFeedback) {}

  public CompletableFuture<AiEvaluationResult> gradeAnswerAsync(
      QuizQuestion question, String submittedAnswer, String articleText) {

    return claudeTemplate
        .executeAsyncWithRetry(
            attempt -> {
              boolean isRetry = attempt > 1;

              // 1. 시스템 프롬프트 준비
              String systemPrompt =
                  promptLoader.loadPrompt(
                      "quiz-grading.txt",
                      Map.of(
                          "questionText", question.getQuestionText(),
                          "correctAnswer", question.getCorrectAnswer(),
                          "userAnswer", submittedAnswer));

              if (isRetry) {
                systemPrompt +=
                    "\n\n[Correction Hint]\n이전 응답이 올바른 JSON 형식이 아니었거나 처리 지연이 발생했습니다. 반드시 주어진 JSON 형식만 순수하게 반환하세요.";
              }

              // 2. 사용자 프롬프트 준비 (본문 격리)
              String userPrompt = "<source_content>\n" + articleText + "\n</source_content>";

              // 3. Claude API 호출 (채점 전용 3초 타임아웃 클라이언트 사용)
              return claudeClient.getGradingGeneratedText(systemPrompt, userPrompt);
            },
            ClaudeGradingResponseDto.class,
            dto -> {
              if (dto.getIsCorrect() == null) {
                throw new CustomException(
                    QuizErrorCode.QUIZ_GRADING_FAILED, "AI 응답에 isCorrect 필드가 누락되었습니다.");
              }
            },
            1, // attempt
            2, // maxAttempts
            timeoutDuration,
            gradingExecutor,
            QUIZ_GRADING,
            this::mapToQuizGradingException)
        .thenApply(
            dto ->
                new AiEvaluationResult(
                    question, submittedAnswer, dto.getIsCorrect(), dto.getAiFeedback()));
  }

  private RuntimeException mapToQuizGradingException(Throwable ex) {
    Throwable cause = (ex instanceof CustomException && ex.getCause() != null) ? ex.getCause() : ex;

    if (ex instanceof CustomException) {
      if (((CustomException) ex).getErrorCode() == GlobalErrorCode.AI_PARSING_ERROR) {
        return new CustomException(
            QuizErrorCode.QUIZ_GRADING_FAILED, "AI 채점 응답 JSON 파싱에 실패했습니다.", cause);
      }
      return (CustomException) ex;
    }
    if (ex instanceof TimeoutException) {
      return new CustomException(QuizErrorCode.QUIZ_TIMEOUT, "AI 채점 중 타임아웃이 발생했습니다.", cause);
    }
    if (ex instanceof JsonProcessingException || ex.getCause() instanceof JsonProcessingException) {
      return new CustomException(
          QuizErrorCode.QUIZ_GRADING_FAILED, "AI 채점 응답 JSON 파싱에 실패했습니다.", cause);
    }
    return new CustomException(
        QuizErrorCode.QUIZ_GRADING_FAILED, "AI 채점 서비스 연동 중 오류가 발생했습니다.", cause);
  }
}
