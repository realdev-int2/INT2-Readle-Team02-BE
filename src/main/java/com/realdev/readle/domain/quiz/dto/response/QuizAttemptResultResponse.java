package com.realdev.readle.domain.quiz.dto.response;

import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizAttemptResultResponse {
  private final Long reportId;
  private final Long quizSetId;
  private final Long attemptId;
  private final String title;
  private final List<String> tags;
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
    private final Integer orderNo;
    private final String questionType;
    private final String questionText;
    private final String submittedAnswer;
    private final Boolean isCorrect;
    private final String aiFeedback;

    public static QuestionResult from(QuizAnswer answer) {
      String submittedAnswerText = answer.getSubmittedAnswerText();
      if (answer.getSubmittedChoice() != null) {
        submittedAnswerText = answer.getSubmittedChoice().getChoiceText();
      }

      String questionTypeStr =
          answer.getQuizQuestion().getQuestionType() != null
              ? answer.getQuizQuestion().getQuestionType().name().toLowerCase()
              : null;

      return QuestionResult.builder()
          .questionId(answer.getQuizQuestion().getId())
          .orderNo(answer.getQuizQuestion().getOrderNo())
          .questionType(questionTypeStr)
          .questionText(answer.getQuizQuestion().getQuestionText())
          .submittedAnswer(submittedAnswerText)
          .isCorrect(answer.getIsCorrect())
          .aiFeedback(answer.getAiFeedback())
          .build();
    }
  }

  public static QuizAttemptResultResponse from(
      QuizResult result,
      List<QuizAnswer> answers,
      String title,
      List<String> tags,
      Long quizSetId,
      Long attemptId,
      Long reportId) {
    List<QuestionResult> resultList = answers.stream().map(QuestionResult::from).toList();

    return QuizAttemptResultResponse.builder()
        .reportId(reportId)
        .quizSetId(quizSetId)
        .attemptId(attemptId)
        .title(title)
        .tags(tags != null ? tags : List.of())
        .accuracyRate(result.getAccuracyRate())
        .correctCount(result.getCorrectCount())
        .totalCount(result.getTotalCount())
        .solveDurationSeconds(result.getSolveDurationSeconds())
        .completedAt(result.getCompletedAt())
        .results(resultList)
        .build();
  }
}
