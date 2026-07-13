package com.realdev.readle.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_choice",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_choice_order",
          columnNames = {"question_id", "order_no"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizChoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private QuizQuestion quizQuestion;

  @Column(name = "order_no", nullable = false, columnDefinition = "SMALLINT")
  private Integer orderNo;

  @Column(name = "choice_text", nullable = false, columnDefinition = "TEXT")
  private String choiceText;

  @Column(name = "is_correct", nullable = false)
  private Boolean isCorrect;

  private QuizChoice(
      QuizQuestion quizQuestion, Integer orderNo, String choiceText, Boolean isCorrect) {
    this.quizQuestion = quizQuestion;
    this.orderNo = orderNo;
    this.choiceText = choiceText;
    this.isCorrect = isCorrect;
  }

  public static QuizChoice create(
      QuizQuestion quizQuestion, Integer orderNo, String choiceText, Boolean isCorrect) {
    return new QuizChoice(quizQuestion, orderNo, choiceText, isCorrect);
  }
}
