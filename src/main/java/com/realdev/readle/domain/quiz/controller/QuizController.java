package com.realdev.readle.domain.quiz.controller;

import com.realdev.readle.domain.quiz.dto.request.QuizCreateRequest;
import com.realdev.readle.domain.quiz.dto.response.QuizCreateResponse;
import com.realdev.readle.domain.quiz.service.QuizGenerationService;
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

  @PostMapping
  public ResponseEntity<QuizCreateResponse> createQuiz(
      @Valid @RequestBody QuizCreateRequest request) {
    QuizCreateResponse response =
        quizGenerationService.createQuizSet(request.getSourceValidationId());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
