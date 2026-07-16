package com.realdev.readle.domain.quiz.dto.response;

import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuizAttemptStartResponse {
  private Long attemptId;
  private Long quizId;
  private String status;
  private LocalDateTime startedAt;

  public static QuizAttemptStartResponse of(QuizAttempt attempt) {
    return QuizAttemptStartResponse.builder()
        .attemptId(attempt.getId())
        .quizId(attempt.getQuizSet().getId())
        .status(attempt.getStatus().name().toLowerCase(Locale.ROOT))
        .startedAt(attempt.getStartedAt())
        .build();
  }
}
