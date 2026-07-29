package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.ResultReportHistoryRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.dto.response.ResultReportHistoryResponse;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.domain.quiz.service.ResultReportService;
import com.realdev.readle.global.config.OpenApiConfig;
import com.realdev.readle.global.exception.dto.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ResultReport", description = "결과 리포트 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/result-reports")
public class ResultReportController {

  private final QuizSolveService quizSolveService;
  private final ResultReportService resultReportService;

  @Operation(
      summary = "학습 히스토리 목록 조회",
      description = "로그인 사용자의 제출 완료 결과 리포트를 조회합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME),
      responses =
          @ApiResponse(
              responseCode = "401",
              description = "인증 실패",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ErrorResponse.class))))
  @GetMapping
  public ResponseEntity<ResultReportHistoryResponse> getResultReports(
      @AuthenticationPrincipal String memberUuid,
      @Valid @ParameterObject @ModelAttribute ResultReportHistoryRequest request) {
    return ResponseEntity.ok(
        resultReportService.getHistory(
            memberUuid, request.cursor(), request.size(), request.sort(), request.tagId()));
  }

  @Operation(
      summary = "결과 리포트 상세 조회",
      description = "결과 리포트 ID로 퀴즈 풀이 결과를 조회합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME),
      responses =
          @ApiResponse(
              responseCode = "401",
              description = "인증 실패",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ErrorResponse.class))))
  @GetMapping("/{reportId}")
  public ResponseEntity<QuizAttemptResultResponse> getResultReport(
      @PathVariable("reportId") Long reportId, @AuthenticationPrincipal String memberUuid) {
    QuizAttemptResultResponse response = quizSolveService.getResultReport(memberUuid, reportId);
    return ResponseEntity.ok(response);
  }
}
