package com.realdev.readle.domain.quiz.entity;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;

@Entity
@Table(
    name = "quiz_set",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"source_validation_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSet extends BaseCreatedAtEntity {

  private static final int MIN_QUESTION_COUNT = 1;
  private static final int MAX_QUESTION_COUNT = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private QuizSetStatus status;

  @Column(name = "question_count", columnDefinition = "SMALLINT")
  private Integer questionCount;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "is_bypassed", nullable = false)
  private Boolean isBypassed;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_validation_id", nullable = false)
  private ContentValidation sourceValidation;

  private QuizSet(Content content, ContentValidation sourceValidation, Boolean isBypassed) {
    validateIntegration(content, sourceValidation);
    this.content = content;
    this.sourceValidation = sourceValidation;
    this.isBypassed = isBypassed;
    this.status = QuizSetStatus.GENERATING;
  }

  public static QuizSet create(
      Content content, ContentValidation sourceValidation, Boolean isBypassed) {
    return new QuizSet(content, sourceValidation, isBypassed);
  }

  public void complete(int questionCount) {
    if (this.status != QuizSetStatus.GENERATING) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "이미 처리된 퀴즈 세트입니다. 현재 상태: " + this.status);
    }
    if (questionCount < MIN_QUESTION_COUNT || questionCount > MAX_QUESTION_COUNT) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT,
          "퀴즈 문항 수는 " + MIN_QUESTION_COUNT + "개에서 " + MAX_QUESTION_COUNT + "개 사이여야 합니다.");
    }
    this.status = QuizSetStatus.COMPLETED;
    this.questionCount = questionCount;
    this.completedAt = LocalDateTime.now();
  }

  public void fail() {
    if (this.status != QuizSetStatus.GENERATING) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "이미 처리된 퀴즈 세트입니다. 현재 상태: " + this.status);
    }
    this.status = QuizSetStatus.FAILED;
    this.completedAt = LocalDateTime.now();
  }

  private void validateIntegration(Content content, ContentValidation sourceValidation) {
    if (content == null || sourceValidation == null) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "Content와 ContentValidation은 필수 입력값입니다.");
    }
    if (!sourceValidation.getContent().getId().equals(content.getId())) {
      throw new CustomException(GlobalErrorCode.INVALID_INPUT, "검증 정보(sourceValidation)의 콘텐츠와 퀴즈 세트의 콘텐츠가 일치하지 않습니다.");
    }
  }
}
