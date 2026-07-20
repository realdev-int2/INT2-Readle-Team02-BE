package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ResultReport", description = "결과 리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/result-reports")
public class ResultReportController {

  private final QuizSolveService quizSolveService;

  @Operation(summary = "결과 리포트 상세 조회", description = "결과 리포트 ID(풀이 시도 ID)로 퀴즈 풀이 결과를 조회합니다.")
  @GetMapping("/{reportId}")
  public ResponseEntity<QuizAttemptResultResponse> getResultReport(
      @PathVariable("reportId") Long reportId, @AuthenticationPrincipal String memberUuid) {
    QuizAttemptResultResponse response = quizSolveService.getAttemptResult(memberUuid, reportId);
    return ResponseEntity.ok(response);
  }
}
