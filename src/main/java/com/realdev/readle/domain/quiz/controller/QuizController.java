package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.QuizCreateRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizCreateResponse;
import com.realdev.readle.domain.quiz.service.QuizGenerationService;
import com.realdev.readle.global.config.OpenApiConfig;
import com.realdev.readle.global.exception.dto.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

  private final QuizGenerationService quizGenerationService;

  @Operation(
      summary = "퀴즈 생성",
      description = "검증된 콘텐츠로 퀴즈 세트를 생성합니다.",
      security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME),
      responses =
          @ApiResponse(
              responseCode = "401",
              description = "인증 실패",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = ErrorResponse.class))))
  @PostMapping
  public ResponseEntity<QuizCreateResponse> createQuiz(
      @Valid @RequestBody QuizCreateRequest request) {
    QuizCreateResponse response =
        quizGenerationService.createQuizSet(request.getSourceValidationId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
