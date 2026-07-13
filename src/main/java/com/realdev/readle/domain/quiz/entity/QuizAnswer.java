package com.realdev.readle.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_answer",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_answer_attempt_question",
          columnNames = {"attempt_id", "question_id"})
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private QuizAttempt quizAttempt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false)
  private QuizQuestion quizQuestion;

  @Column(name = "submitted_answer_text", columnDefinition = "TEXT")
  private String submittedAnswerText;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submitted_choice_id")
  private QuizChoice submittedChoice;

  @Column(name = "is_correct", nullable = false)
  private Boolean isCorrect;

  @Column(name = "ai_feedback", columnDefinition = "TEXT")
  private String aiFeedback;

  @Column(name = "evaluated_at", nullable = false)
  private LocalDateTime evaluatedAt;

  private QuizAnswer(
      QuizAttempt quizAttempt,
      QuizQuestion quizQuestion,
      String submittedAnswerText,
      QuizChoice submittedChoice,
      Boolean isCorrect,
      String aiFeedback,
      LocalDateTime evaluatedAt) {
    this.quizAttempt = quizAttempt;
    this.quizQuestion = quizQuestion;
    this.submittedAnswerText = submittedAnswerText;
    this.submittedChoice = submittedChoice;
    this.isCorrect = isCorrect;
    this.aiFeedback = aiFeedback;
    this.evaluatedAt = evaluatedAt;
  }

  public static QuizAnswer createForWritten(
      QuizAttempt quizAttempt,
      QuizQuestion quizQuestion,
      String submittedAnswerText,
      Boolean isCorrect,
      String aiFeedback) {
    return new QuizAnswer(
        quizAttempt,
        quizQuestion,
        submittedAnswerText,
        null,
        isCorrect,
        aiFeedback,
        LocalDateTime.now());
  }

  public static QuizAnswer createForChoice(
      QuizAttempt quizAttempt,
      QuizQuestion quizQuestion,
      QuizChoice submittedChoice,
      Boolean isCorrect) {
    if (submittedChoice == null
        || !submittedChoice.getQuizQuestion().getId().equals(quizQuestion.getId())) {
      throw new IllegalArgumentException("선택한 답안이 해당 문제에 속하지 않습니다.");
    }
    return new QuizAnswer(
        quizAttempt, quizQuestion, null, submittedChoice, isCorrect, null, LocalDateTime.now());
  }
}
