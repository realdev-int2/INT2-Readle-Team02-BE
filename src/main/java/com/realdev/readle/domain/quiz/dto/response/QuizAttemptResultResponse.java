package com.realdev.readle.domain.quiz.dto.response;

import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final Integer correctChoiceNo;
    private final String correctChoiceText;

    public static QuestionResult from(QuizAnswer answer, QuizChoice correctChoice) {
      String submittedAnswerText = answer.getSubmittedAnswerText();
      if (answer.getSubmittedChoice() != null) {
        submittedAnswerText = answer.getSubmittedChoice().getChoiceText();
      }

      String questionTypeStr =
          answer.getQuizQuestion().getQuestionType() != null
              ? answer.getQuizQuestion().getQuestionType().name().toLowerCase()
              : null;

      Integer correctNo = null;
      String correctText = null;

      if (answer.getQuizQuestion().getQuestionType() == QuestionType.MULTIPLE_CHOICE
          && correctChoice != null) {
        correctNo = correctChoice.getOrderNo();
        correctText = correctChoice.getChoiceText();
      }

      return QuestionResult.builder()
          .questionId(answer.getQuizQuestion().getId())
          .orderNo(answer.getQuizQuestion().getOrderNo())
          .questionType(questionTypeStr)
          .questionText(answer.getQuizQuestion().getQuestionText())
          .submittedAnswer(submittedAnswerText)
          .isCorrect(answer.getIsCorrect())
          .aiFeedback(answer.getAiFeedback())
          .correctChoiceNo(correctNo)
          .correctChoiceText(correctText)
          .build();
    }

    public static QuestionResult from(QuizAnswer answer) {
      return from(answer, null);
    }
  }

  public static QuizAttemptResultResponse from(
      QuizResult result,
      List<QuizAnswer> answers,
      Map<Long, QuizChoice> correctChoiceMap,
      String title,
      List<String> tags,
      Long quizSetId,
      Long attemptId,
      Long reportId) {
    List<QuestionResult> resultList =
        answers.stream()
            .map(
                ans ->
                    QuestionResult.from(
                        ans,
                        correctChoiceMap != null
                            ? correctChoiceMap.get(ans.getQuizQuestion().getId())
                            : null))
            .toList();

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

  public static QuizAttemptResultResponse from(
      QuizResult result,
      List<QuizAnswer> answers,
      String title,
      List<String> tags,
      Long quizSetId,
      Long attemptId,
      Long reportId) {
    return from(result, answers, Map.of(), title, tags, quizSetId, attemptId, reportId);
  }
}
