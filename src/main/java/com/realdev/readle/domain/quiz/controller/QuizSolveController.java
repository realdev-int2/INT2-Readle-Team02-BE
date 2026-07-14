package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.QuizAttemptStartResponse;
import com.realdev.readle.domain.quiz.dto.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

  private Long getCurrentMemberId() {
    try {
      return Long.valueOf(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName());
    } catch (Exception e) {
      return 1L; // Fallback
    }
  }

  @PostMapping("/{quizSetId}/attempts")
  public ResponseEntity<QuizAttemptStartResponse> startQuiz(@PathVariable("quizSetId") Long quizSetId) {
    Long memberId = getCurrentMemberId();
    Long attemptId = quizSolveService.startQuiz(quizSetId, memberId);
    return ResponseEntity.ok(QuizAttemptStartResponse.of(attemptId));
  }

  @GetMapping("/attempts/{attemptId}")
  public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(
      @PathVariable("attemptId") Long attemptId) {
    Long memberId = getCurrentMemberId();
    QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberId);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public ResponseEntity<QuizSubmitResponse> submitAnswers(
      @PathVariable("attemptId") Long attemptId, @Valid @RequestBody QuizSubmitRequest request) {
    Long memberId = getCurrentMemberId();
    QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberId, request);
    return ResponseEntity.ok(response);
  }
}
