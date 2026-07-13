package com.realdev.readle.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_question",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_question_order",
          columnNames = {"quiz_set_id", "order_no"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_set_id", nullable = false)
  private QuizSet quizSet;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false, length = 20)
  private QuestionType questionType;

  @Column(name = "order_no", nullable = false, columnDefinition="SMALLINT")
  private Integer orderNo;

  @Lob
  @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
  private String questionText;

  @Lob
  @Column(name = "code_snippet", columnDefinition = "TEXT")
  private String codeSnippet;

  @Lob
  @Column(name = "correct_answer", columnDefinition = "TEXT")
  private String correctAnswer;

  @Lob
  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  @Lob
  @Column(name = "source_excerpt", columnDefinition = "TEXT")
  private String sourceExcerpt;

  private QuizQuestion(
      QuizSet quizSet,
      Integer orderNo,
      QuestionType questionType,
      String questionText,
      String codeSnippet,
      String correctAnswer,
      String explanation,
      String sourceExcerpt) {
    this.quizSet = quizSet;
    this.orderNo = orderNo;
    this.questionType = questionType;
    this.questionText = questionText;
    this.codeSnippet = codeSnippet;
    this.correctAnswer = correctAnswer;
    this.explanation = explanation;
    this.sourceExcerpt = sourceExcerpt;
  }

  public static QuizQuestion create(
      QuizSet quizSet,
      Integer orderNo,
      QuestionType questionType,
      String questionText,
      String codeSnippet,
      String correctAnswer,
      String explanation,
      String sourceExcerpt) {
    return new QuizQuestion(
        quizSet,
        orderNo,
        questionType,
        questionText,
        codeSnippet,
        correctAnswer,
        explanation,
        sourceExcerpt);
  }
}
