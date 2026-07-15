package com.realdev.readle.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.quiz.dto.ClaudeGradingResponseDto;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizAiGradingService {

  private final ClaudeClient claudeClient;
  private final PromptLoader promptLoader;
  private final ObjectMapper objectMapper;

  public record AiEvaluationResult(
      QuizQuestion question, String submittedAnswer, boolean isCorrect, String aiFeedback) {}

  public CompletableFuture<AiEvaluationResult> gradeAnswerAsync(
      QuizQuestion question, String submittedAnswer, String articleText) {
    return executeWithTimeoutAndRetry(question, submittedAnswer, articleText, 1);
  }

  private CompletableFuture<AiEvaluationResult> executeWithTimeoutAndRetry(
      QuizQuestion question, String submittedAnswer, String articleText, int retriesLeft) {
    return CompletableFuture.supplyAsync(
            () -> doGrade(question, submittedAnswer, articleText, retriesLeft < 1))
        .orTimeout(5, TimeUnit.SECONDS)
        .exceptionallyCompose(
            ex -> {
              if (retriesLeft > 0) {
                log.warn("AI 채점 실패(타임아웃 또는 오류). 재시도를 진행합니다. 남은 횟수: {}", retriesLeft, ex);
                return executeWithTimeoutAndRetry(
                    question, submittedAnswer, articleText, retriesLeft - 1);
              } else {
                log.error("AI 채점 최종 실패. Fallback 결과로 반환합니다.", ex);
                return CompletableFuture.completedFuture(
                    new AiEvaluationResult(
                        question,
                        submittedAnswer,
                        false,
                        "AI 채점 서버와의 연결이 원활하지 않아 채점(JSON 변환 등)에 실패했습니다."));
              }
            });
  }

  private AiEvaluationResult doGrade(
      QuizQuestion question, String submittedAnswer, String articleText, boolean isRetry) {

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
          "\n\n[Correction Hint]\n이전 응답이 올바른 JSON 형식이 아니었습니다. 반드시 주어진 JSON 형식만 순수하게 반환하세요.";
    }

    // 2. 사용자 프롬프트 준비 (본문 격리)
    String userPrompt = "<source_content>\n" + articleText + "\n</source_content>";

    // 3. Claude API 호출
    String jsonResponse = claudeClient.getGeneratedText(systemPrompt, userPrompt);

    // 4. 응답 파싱 및 검증
    ClaudeGradingResponseDto responseDto = parseAndValidate(jsonResponse);

    return new AiEvaluationResult(
        question, submittedAnswer, responseDto.getIsCorrect(), responseDto.getAiFeedback());
  }

  private ClaudeGradingResponseDto parseAndValidate(String jsonResponse) {
    try {
      if (jsonResponse.startsWith("```json")) {
        jsonResponse = jsonResponse.substring(7);
        if (jsonResponse.endsWith("```")) {
          jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
        }
      }
      jsonResponse = jsonResponse.trim();

      ClaudeGradingResponseDto response =
          objectMapper.readValue(jsonResponse, ClaudeGradingResponseDto.class);

      if (response.getIsCorrect() == null) {
        throw new CustomException(
            com.realdev.readle.domain.quiz.exception.QuizErrorCode.QUIZ_GENERATION_FAILED,
            "AI 응답에 isCorrect 필드가 누락되었습니다.");
      }

      return response;
    } catch (JsonProcessingException e) {
      throw new CustomException(
          com.realdev.readle.domain.quiz.exception.QuizErrorCode.QUIZ_GENERATION_FAILED,
          "AI 채점 응답 JSON 파싱에 실패했습니다.",
          e);
    }
  }
}
