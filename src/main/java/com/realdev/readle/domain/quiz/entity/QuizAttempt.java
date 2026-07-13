package com.realdev.readle.domain.quiz.entity;

import com.realdev.readle.domain.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_attempt",
    indexes = {@Index(name = "idx_attempt_member_started", columnList = "member_id, started_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "quiz_set_id", nullable = false)
  private QuizSet quizSet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private AttemptStatus status;

  @Column(name = "started_at", nullable = false, updatable = false)
  private LocalDateTime startedAt;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  private QuizAttempt(QuizSet quizSet, Member member) {
    this.quizSet = quizSet;
    this.member = member;
    this.status = AttemptStatus.IN_PROGRESS;
    this.startedAt = LocalDateTime.now();
  }

  public static QuizAttempt create(QuizSet quizSet, Member member) {
    return new QuizAttempt(quizSet, member);
  }

  public void submit() {
    if (this.status != AttemptStatus.IN_PROGRESS) {
      throw new IllegalStateException("이미 제출된 퀴즈 시도입니다.");
    }
    this.status = AttemptStatus.SUBMITTED;
    this.submittedAt = LocalDateTime.now();
  }
}
