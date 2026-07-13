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

  @Column(name = "order_no", nullable = false)
  private short orderNo;

  @Lob
  @Column(name = "question_text", nullable = false)
  private String questionText;

  @Lob
  @Column(name = "code_snippet")
  private String codeSnippet;

  @Lob
  @Column(name = "correct_answer")
  private String correctAnswer;

  @Lob
  @Column(name = "explanation")
  private String explanation;

  @Lob
  @Column(name = "source_excerpt")
  private String sourceExcerpt;
}
