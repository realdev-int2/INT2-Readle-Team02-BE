package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptStartResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.response.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import jakarta.validation.Valid;
import java.security.Principal;
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

  private String getMemberUuid(Principal principal) {
    if (principal == null
        || principal.getName() == null
        || principal.getName().isBlank()
        || "anonymousUser".equals(principal.getName())) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN, "인증되지 않은 사용자입니다.");
    }
    return principal.getName();
  }

  @PostMapping("/{quizSetId}/attempts")
  public ResponseEntity<QuizAttemptStartResponse> startQuiz(
      @PathVariable("quizSetId") Long quizSetId, Principal principal) {
    String memberUuid = getMemberUuid(principal);
    Long attemptId = quizSolveService.startQuiz(quizSetId, memberUuid);
    return ResponseEntity.ok(QuizAttemptStartResponse.of(attemptId));
  }

  @GetMapping("/attempts/{attemptId}")
  public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(
      @PathVariable("attemptId") Long attemptId, Principal principal) {
    String memberUuid = getMemberUuid(principal);
    QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberUuid);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public ResponseEntity<QuizSubmitResponse> submitAnswers(
      @PathVariable("attemptId") Long attemptId,
      @Valid @RequestBody QuizSubmitRequest request,
      Principal principal) {
    String memberUuid = getMemberUuid(principal);
    QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberUuid, request);
    return ResponseEntity.ok(response);
  }
}
