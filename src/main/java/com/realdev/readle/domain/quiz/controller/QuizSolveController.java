package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptStartResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Quiz", description = "퀴즈 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quizzes")
public class QuizSolveController {

  private final QuizSolveService quizSolveService;

  @Operation(summary = "퀴즈 풀이 시작", description = "새로운 퀴즈 풀이 시도(Attempt)를 생성합니다.")
  @PostMapping("/{quizSetId}/attempts")
  public ResponseEntity<QuizAttemptStartResponse> startQuiz(
      @PathVariable("quizSetId") Long quizSetId, @AuthenticationPrincipal String memberUuid) {
    QuizAttempt attempt = quizSolveService.startQuiz(quizSetId, memberUuid);
    return ResponseEntity.ok(QuizAttemptStartResponse.of(attempt));
  }

  @Operation(summary = "퀴즈 세트 상세 조회", description = "풀이 시도의 퀴즈 세트 문제 목록을 조회합니다.")
  @GetMapping("/attempts/{attemptId}")
  public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(
      @PathVariable("attemptId") Long attemptId, @AuthenticationPrincipal String memberUuid) {
    QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberUuid);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "퀴즈 풀이 답안 제출 및 채점", description = "제출한 답안을 채점하고 결과를 저장합니다.")
  @PostMapping("/attempts/{attemptId}/submit")
  public ResponseEntity<QuizSubmitResponse> submitAnswers(
      @PathVariable("attemptId") Long attemptId,
      @Valid @RequestBody QuizSubmitRequest request,
      @AuthenticationPrincipal String memberUuid) {
    QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberUuid, request);
    return ResponseEntity.ok(response);
  }

  @Operation(summary = "퀴즈 풀이 결과 상세 조회", description = "완료된 퀴즈 풀이 시도의 최종 결과와 정오표 및 AI 피드백을 조회합니다.")
  @GetMapping("/attempts/{attemptId}/result")
  public ResponseEntity<QuizAttemptResultResponse> getAttemptResult(
      @PathVariable("attemptId") Long attemptId, @AuthenticationPrincipal String memberUuid) {
    QuizAttemptResultResponse response = quizSolveService.getAttemptResult(memberUuid, attemptId);
    return ResponseEntity.ok(response);
  }
}
