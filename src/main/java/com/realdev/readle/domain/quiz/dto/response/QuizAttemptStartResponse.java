package com.realdev.readle.domain.quiz.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptStartResponse {
  private Long attemptId;

  public static QuizAttemptStartResponse of(Long attemptId) {
    return new QuizAttemptStartResponse(attemptId);
  }
}
