package com.realdev.readle.domain.quiz.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QuizAttemptResultResponseTest {

  @Test
  @DisplayName("객관식 문항은 correctChoiceNo와 correctChoiceText가 매핑된다")
  void questionResult_MultipleChoice_ExposesCorrectChoice() {
    QuizQuestion question = mock(QuizQuestion.class);
    given(question.getId()).willReturn(10L);
    given(question.getOrderNo()).willReturn(1);
    given(question.getQuestionType()).willReturn(QuestionType.MULTIPLE_CHOICE);
    given(question.getQuestionText()).willReturn("싱글톤 스코프에 관한 설명 중 옳은 것은?");

    QuizChoice submittedChoice = mock(QuizChoice.class);
    given(submittedChoice.getChoiceText()).willReturn("프로토타입 스코프이다");

    QuizChoice correctChoice = mock(QuizChoice.class);
    given(correctChoice.getOrderNo()).willReturn(2);
    given(correctChoice.getChoiceText()).willReturn("하나의 인스턴스만 생성되어 공유된다");

    QuizAnswer answer = mock(QuizAnswer.class);
    given(answer.getQuizQuestion()).willReturn(question);
    given(answer.getSubmittedChoice()).willReturn(submittedChoice);
    given(answer.getIsCorrect()).willReturn(false);
    given(answer.getAiFeedback()).willReturn(null);

    QuizAttemptResultResponse.QuestionResult result =
        QuizAttemptResultResponse.QuestionResult.from(answer, correctChoice);

    assertThat(result.getQuestionId()).isEqualTo(10L);
    assertThat(result.getQuestionType()).isEqualTo("multiple_choice");
    assertThat(result.getSubmittedAnswer()).isEqualTo("프로토타입 스코프이다");
    assertThat(result.getIsCorrect()).isFalse();
    assertThat(result.getAiFeedback()).isNull();
    assertThat(result.getCorrectChoiceNo()).isEqualTo(2);
    assertThat(result.getCorrectChoiceText()).isEqualTo("하나의 인스턴스만 생성되어 공유된다");
  }

  @ParameterizedTest
  @EnumSource(
      value = QuestionType.class,
      names = {"SHORT_ANSWER", "CODE_BLANK"})
  @DisplayName("주관식 및 코드빈칸 문항은 correctChoiceNo와 correctChoiceText가 null로 은닉된다")
  void questionResult_NonMultipleChoice_KeepsCorrectAnswerHidden(QuestionType type) {
    QuizQuestion question = mock(QuizQuestion.class);
    given(question.getId()).willReturn(11L);
    given(question.getOrderNo()).willReturn(2);
    given(question.getQuestionType()).willReturn(type);
    given(question.getQuestionText()).willReturn("스프링 빈 생성주기 콜백 메서드는?");

    QuizAnswer answer = mock(QuizAnswer.class);
    given(answer.getQuizQuestion()).willReturn(question);
    given(answer.getSubmittedAnswerText()).willReturn("@PostConstruct");
    given(answer.getSubmittedChoice()).willReturn(null);
    given(answer.getIsCorrect()).willReturn(true);
    given(answer.getAiFeedback()).willReturn("정답입니다");

    QuizAttemptResultResponse.QuestionResult result =
        QuizAttemptResultResponse.QuestionResult.from(answer, null);

    assertThat(result.getQuestionId()).isEqualTo(11L);
    assertThat(result.getQuestionType()).isEqualTo(type.name().toLowerCase());
    assertThat(result.getSubmittedAnswer()).isEqualTo("@PostConstruct");
    assertThat(result.getIsCorrect()).isTrue();
    assertThat(result.getAiFeedback()).isEqualTo("정답입니다");
    assertThat(result.getCorrectChoiceNo()).isNull();
    assertThat(result.getCorrectChoiceText()).isNull();
  }

  @Test
  @DisplayName("from 팩토리 메서드 - correctChoiceMap을 사용하여 결과 리포트 응답을 생성한다")
  void from_WithCorrectChoiceMap() {
    QuizResult result = mock(QuizResult.class);
    given(result.getAccuracyRate()).willReturn(new BigDecimal("50.00"));
    given(result.getCorrectCount()).willReturn(1);
    given(result.getTotalCount()).willReturn(2);
    given(result.getSolveDurationSeconds()).willReturn(45);
    given(result.getCompletedAt()).willReturn(LocalDateTime.now());

    QuizQuestion question = mock(QuizQuestion.class);
    given(question.getId()).willReturn(10L);
    given(question.getOrderNo()).willReturn(1);
    given(question.getQuestionType()).willReturn(QuestionType.MULTIPLE_CHOICE);

    QuizAnswer answer = mock(QuizAnswer.class);
    given(answer.getQuizQuestion()).willReturn(question);

    QuizChoice correctChoice = mock(QuizChoice.class);
    given(correctChoice.getOrderNo()).willReturn(3);
    given(correctChoice.getChoiceText()).willReturn("정답 선택지");

    QuizAttemptResultResponse response =
        QuizAttemptResultResponse.from(
            result,
            List.of(answer),
            Map.of(10L, correctChoice),
            "제목",
            List.of("태그1"),
            100L,
            200L,
            300L);

    assertThat(response.getReportId()).isEqualTo(300L);
    assertThat(response.getResults()).hasSize(1);
    assertThat(response.getResults().get(0).getCorrectChoiceNo()).isEqualTo(3);
    assertThat(response.getResults().get(0).getCorrectChoiceText()).isEqualTo("정답 선택지");
  }
}
