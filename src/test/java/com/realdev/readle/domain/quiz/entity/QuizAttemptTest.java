package com.realdev.readle.domain.quiz.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.realdev.readle.domain.member.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizAttemptTest {

  @Test
  @DisplayName("GRADING 상태에서 resetToInProgress 호출 시 IN_PROGRESS 상태로 복구된다")
  void resetToInProgress_Success() {
    QuizAttempt attempt = QuizAttempt.createInProgress(mock(QuizSet.class), mock(Member.class));
    attempt.markAsGrading();
    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADING);

    attempt.resetToInProgress();
    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("SUBMITTED 상태에서 resetToInProgress 호출 시 IN_PROGRESS 상태로 복구되고 submittedAt이 초기화된다")
  void resetToInProgress_FromSubmitted() {
    QuizAttempt attempt = QuizAttempt.createInProgress(mock(QuizSet.class), mock(Member.class));
    attempt.submit();
    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
    assertThat(attempt.getSubmittedAt()).isNotNull();

    attempt.resetToInProgress();
    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    assertThat(attempt.getSubmittedAt()).isNull();
  }
}
