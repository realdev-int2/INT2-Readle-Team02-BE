package com.realdev.readle.domain.quiz.dto.response;

import com.realdev.readle.domain.quiz.entity.QuizSet;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizCreateResponse {

  private Long quizId;
  private String status;
  private Integer questionCount;
  private LocalDateTime createdAt;

  public static QuizCreateResponse from(QuizSet quizSet) {
    return QuizCreateResponse.builder()
        .quizId(quizSet.getId())
        .status(quizSet.getStatus().name().toLowerCase(Locale.ROOT))
        .questionCount(quizSet.getQuestionCount())
        .createdAt(quizSet.getCreatedAt())
        .build();
  }
}
