package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.QuizAttemptStartResponse;
import com.realdev.readle.domain.quiz.dto.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
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

  private String getCurrentMemberUuid() {
    try {
      String uuid =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication()
              .getName();
      if (uuid == null || uuid.isBlank() || "anonymousUser".equals(uuid)) {
        throw new CustomException(GlobalErrorCode.FORBIDDEN, "인증되지 않은 사용자입니다.");
      }
      return uuid;
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(GlobalErrorCode.FORBIDDEN, "인증 정보를 파싱할 수 없습니다.");
    }
  }

  @PostMapping("/{quizSetId}/attempts")
  public ResponseEntity<QuizAttemptStartResponse> startQuiz(
      @PathVariable("quizSetId") Long quizSetId) {
    String memberUuid = getCurrentMemberUuid();
    Long attemptId = quizSolveService.startQuiz(quizSetId, memberUuid);
    return ResponseEntity.ok(QuizAttemptStartResponse.of(attemptId));
  }

  @GetMapping("/attempts/{attemptId}")
  public ResponseEntity<QuizDetailResponse> getQuizAttemptDetail(
      @PathVariable("attemptId") Long attemptId) {
    String memberUuid = getCurrentMemberUuid();
    QuizDetailResponse response = quizSolveService.getQuizAttemptDetail(attemptId, memberUuid);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public ResponseEntity<QuizSubmitResponse> submitAnswers(
      @PathVariable("attemptId") Long attemptId, @Valid @RequestBody QuizSubmitRequest request) {
    String memberUuid = getCurrentMemberUuid();
    QuizSubmitResponse response = quizSolveService.submitAnswers(attemptId, memberUuid, request);
    return ResponseEntity.ok(response);
  }
}
