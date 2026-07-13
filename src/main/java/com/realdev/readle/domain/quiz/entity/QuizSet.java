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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "quiz_set")
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

  @Column(name = "question_count")
  private Short questionCount;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "is_bypassed", nullable = false)
  private boolean bypassed;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_validation_id", nullable = false)
  private ContentValidation sourceValidation;

  private QuizSet(Content content, ContentValidation sourceValidation, boolean isBypassed) {
    this.content = content;
    this.sourceValidation = sourceValidation;
    this.bypassed = isBypassed;
    this.status = QuizSetStatus.GENERATING;
  }

  public static QuizSet create(
      Content content, ContentValidation sourceValidation, boolean isBypassed) {
    return new QuizSet(content, sourceValidation, isBypassed);
  }

  public void complete(int questionCount) {
    if (questionCount < MIN_QUESTION_COUNT || questionCount > MAX_QUESTION_COUNT) {
      throw new IllegalArgumentException(
          "퀴즈 문항 수는 " + MIN_QUESTION_COUNT + "개에서 " + MAX_QUESTION_COUNT + "개 사이여야 합니다.");
    }
    this.status = QuizSetStatus.COMPLETED;
    this.questionCount = (short) questionCount;
    this.completedAt = LocalDateTime.now();
  }

  public void fail() {
    this.status = QuizSetStatus.FAILED;
    this.completedAt = LocalDateTime.now();
  }
}
