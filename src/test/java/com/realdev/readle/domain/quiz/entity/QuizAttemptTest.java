package com.realdev.readle.domain.quiz.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.global.exception.CustomException;
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
  @DisplayName("GRADING 상태가 아닌 경우 resetToInProgress 호출 시 ATTEMPT_ALREADY_SUBMITTED 예외가 발생한다")
  void resetToInProgress_InvalidStatus() {
    QuizAttempt attempt = QuizAttempt.createInProgress(mock(QuizSet.class), mock(Member.class));
    assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);

    assertThatThrownBy(attempt::resetToInProgress)
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.ATTEMPT_ALREADY_SUBMITTED);
  }
}
