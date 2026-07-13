package com.realdev.readle.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_result",
    uniqueConstraints = {@UniqueConstraint(name = "uq_result_attempt", columnNames = "attempt_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attempt_id", nullable = false)
  private QuizAttempt quizAttempt;

  @Column(name = "accuracy_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal accuracyRate;

  @Column(name = "correct_count", nullable = false)
  private short correctCount;

  @Column(name = "total_count", nullable = false)
  private short totalCount;

  @Column(name = "solve_duration_seconds", nullable = false)
  private int solveDurationSeconds;

  @Column(name = "completed_at", nullable = false)
  private LocalDateTime completedAt;
}
