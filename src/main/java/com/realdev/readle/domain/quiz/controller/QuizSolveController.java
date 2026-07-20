package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptStartResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/quizzes")
public class QuizSolveController {

  private final QuizSolveService quizSolveService;

  @PostMapping("/{quizSetId}/attempts")
  public ResponseEntity<QuizAttemptStartResponse> startQuiz(
      @PathVariable("quizSetId") Long quizSetId, @AuthenticationPrincipal String memberUuid) {
    QuizAttempt attempt = quizSolveService.startQuiz(quizSetId, memberUuid);
    return ResponseEntity.ok(QuizAttemptStartResponse.of(attempt));
  }

  @GetMapping("/attempts/{attemptId}")
  public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(
      @PathVariable("attemptId") Long attemptId, @AuthenticationPrincipal String memberUuid) {
    QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberUuid);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public ResponseEntity<QuizSubmitResponse> submitAnswers(
      @PathVariable("attemptId") Long attemptId,
      @Valid @RequestBody QuizSubmitRequest request,
      @AuthenticationPrincipal String memberUuid) {
    QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberUuid, request);
    return ResponseEntity.ok(response);
  }
}
