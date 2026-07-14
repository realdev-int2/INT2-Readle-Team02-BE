package com.realdev.readle.domain.quiz.dto;

import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizDetailResponse {
  private final Long attemptId;
  private final Long quizSetId;
  private final String status;
  private final List<QuestionDetail> questions;

  @Getter
  @Builder
  public static class QuestionDetail {
    private final Long questionId;
    private final QuestionType type;
    private final Integer orderNo;
    private final String questionText;
    private final String codeSnippet;
    private final List<ChoiceDetail> choices;
  }

  @Getter
  @Builder
  public static class ChoiceDetail {
    private final Long choiceId;
    private final Integer orderNo;
    private final String choiceText;
  }

  public static QuizDetailResponse of(
      QuizAttempt attempt, List<QuizQuestion> questions, List<QuizChoice> allChoices) {
    return QuizDetailResponse.builder()
        .attemptId(attempt.getId())
        .quizSetId(attempt.getQuizSet().getId())
        .status(attempt.getStatus().name())
        .questions(
            questions.stream()
                .map(
                    q -> {
                      List<ChoiceDetail> choices =
                          allChoices.stream()
                              .filter(c -> c.getQuizQuestion().getId().equals(q.getId()))
                              .map(
                                  c ->
                                      ChoiceDetail.builder()
                                          .choiceId(c.getId())
                                          .orderNo(c.getOrderNo())
                                          .choiceText(c.getChoiceText())
                                          .build())
                              .collect(Collectors.toList());

                      return QuestionDetail.builder()
                          .questionId(q.getId())
                          .type(q.getQuestionType())
                          .orderNo(q.getOrderNo())
                          .questionText(q.getQuestionText())
                          .codeSnippet(q.getCodeSnippet())
                          .choices(
                              q.getQuestionType() == QuestionType.MULTIPLE_CHOICE ? choices : null)
                          .build();
                    })
                .collect(Collectors.toList()))
        .build();
  }
}
