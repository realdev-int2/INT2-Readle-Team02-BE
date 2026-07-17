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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;

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

  @Column(name = "correct_count", nullable = false, columnDefinition = "SMALLINT")
  private Integer correctCount;

  @Column(name = "total_count", nullable = false, columnDefinition = "SMALLINT")
  private Integer totalCount;

  @Column(name = "solve_duration_seconds", nullable = false)
  private Integer solveDurationSeconds;

  @Column(name = "completed_at", nullable = false)
  private LocalDateTime completedAt;

  private QuizResult(
      QuizAttempt quizAttempt,
      BigDecimal accuracyRate,
      Integer correctCount,
      Integer totalCount,
      Integer solveDurationSeconds) {
    this.quizAttempt = quizAttempt;
    this.accuracyRate = accuracyRate;
    this.correctCount = correctCount;
    this.totalCount = totalCount;
    this.solveDurationSeconds = solveDurationSeconds;
    this.completedAt = LocalDateTime.now();
  }

  public static QuizResult create(
      QuizAttempt quizAttempt,
      Integer correctCount,
      Integer totalCount,
      Integer solveDurationSeconds) {
    if (totalCount == null || totalCount <= 0) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "전체 문항 수는 0보다 커야 합니다.");
    }
    if (correctCount == null || correctCount < 0) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "맞은 문항 수는 0보다 크거나 같아야 합니다.");
    }
    if (correctCount > totalCount) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "맞은 문항 수는 전체 문항 수보다 클 수 없습니다.");
    }
    if (solveDurationSeconds == null || solveDurationSeconds < 0) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "풀이 소요 시간은 0보다 크거나 같아야 합니다.");
    }

    BigDecimal accuracyRate =
        BigDecimal.valueOf(correctCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

    return new QuizResult(
        quizAttempt, accuracyRate, correctCount, totalCount, solveDurationSeconds);
  }
}
