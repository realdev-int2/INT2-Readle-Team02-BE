package com.realdev.readle.domain.quiz.dto.response;

import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizSubmitResponse {
  private final Long reportId;
  private final Long attemptId;
  private final String gradingStatus;
  private final BigDecimal accuracyRate;
  private final Integer correctCount;
  private final Integer totalCount;
  private final Integer solveDurationSeconds;
  private final LocalDateTime completedAt;
  private final List<QuestionResult> results;

  @Getter
  @Builder
  public static class QuestionResult {
    private final Long questionId;
    private final Boolean isCorrect;
    private final String aiFeedback;

    public static QuestionResult from(QuizAnswer answer) {
      return QuestionResult.builder()
          .questionId(answer.getQuizQuestion().getId())
          .isCorrect(answer.getIsCorrect())
          .aiFeedback(answer.getAiFeedback())
          .build();
    }
  }

  public static QuizSubmitResponse from(
      QuizResult result, List<QuizAnswer> staticAnswers, List<QuizAnswer> aiAnswers) {
    List<QuestionResult> results =
        Stream.concat(staticAnswers.stream(), aiAnswers.stream())
            .sorted(java.util.Comparator.comparing(a -> a.getQuizQuestion().getOrderNo()))
            .map(QuestionResult::from)
            .toList();

    return QuizSubmitResponse.builder()
        .reportId(result.getId())
        .attemptId(result.getQuizAttempt().getId())
        .gradingStatus("completed")
        .accuracyRate(result.getAccuracyRate())
        .correctCount(result.getCorrectCount())
        .totalCount(result.getTotalCount())
        .solveDurationSeconds(result.getSolveDurationSeconds())
        .completedAt(result.getCompletedAt())
        .results(results)
        .build();
  }
}
