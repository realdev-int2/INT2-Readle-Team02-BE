package com.realdev.readle.domain.quiz.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.quiz.dto.QuizDetailResponse;
import com.realdev.readle.domain.quiz.dto.QuizSubmitRequest;
import com.realdev.readle.domain.quiz.dto.QuizSubmitResponse;
import com.realdev.readle.domain.quiz.entity.AttemptStatus;
import com.realdev.readle.domain.quiz.entity.QuestionType;
import com.realdev.readle.domain.quiz.entity.QuizAnswer;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizChoice;
import com.realdev.readle.domain.quiz.entity.QuizQuestion;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.repository.QuizAnswerRepository;
import com.realdev.readle.domain.quiz.repository.QuizAttemptRepository;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizResultRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class QuizSolveServiceTest {

    @InjectMocks
    private QuizSolveService quizSolveService;

    @Mock
    private QuizSetRepository quizSetRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private QuizQuestionRepository quizQuestionRepository;
    @Mock
    private QuizChoiceRepository quizChoiceRepository;
    @Mock
    private QuizAnswerRepository quizAnswerRepository;
    @Mock
    private QuizResultRepository quizResultRepository;
    @Mock
    private MemberRepository memberRepository;

    private Member member;
    private QuizSet quizSet;
    private QuizAttempt quizAttempt;
    private QuizQuestion question1;
    private QuizQuestion question2;
    private QuizChoice choice1;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(1L);

        com.realdev.readle.domain.content.entity.Content content = mock(com.realdev.readle.domain.content.entity.Content.class);
        lenient().when(content.getMember()).thenReturn(member);
        
        quizSet = mock(QuizSet.class);
        ReflectionTestUtils.setField(quizSet, "id", 100L);
        lenient().when(quizSet.getContent()).thenReturn(content);

        quizAttempt = mock(QuizAttempt.class);
        ReflectionTestUtils.setField(quizAttempt, "id", 200L);
        lenient().when(quizAttempt.getQuizSet()).thenReturn(quizSet);
        lenient().when(quizAttempt.getMember()).thenReturn(member);
        lenient().when(quizAttempt.getStatus()).thenReturn(AttemptStatus.IN_PROGRESS);
        lenient().when(quizAttempt.getStartedAt()).thenReturn(java.time.LocalDateTime.now().minusMinutes(5));

        question1 = mock(QuizQuestion.class);
        lenient().when(question1.getId()).thenReturn(10L);
        lenient().when(question1.getQuestionType()).thenReturn(QuestionType.MULTIPLE_CHOICE);
        lenient().when(question1.getOrderNo()).thenReturn(1);
        lenient().when(question1.getQuizSet()).thenReturn(quizSet);

        choice1 = mock(QuizChoice.class);
        lenient().when(choice1.getId()).thenReturn(50L);
        lenient().when(choice1.getQuizQuestion()).thenReturn(question1);
        lenient().when(choice1.getIsCorrect()).thenReturn(true);

        question2 = mock(QuizQuestion.class);
        lenient().when(question2.getId()).thenReturn(11L);
        lenient().when(question2.getQuestionType()).thenReturn(QuestionType.SHORT_ANSWER);
        lenient().when(question2.getCorrectAnswer()).thenReturn("스프링");
        lenient().when(question2.getOrderNo()).thenReturn(2);
        lenient().when(question2.getQuizSet()).thenReturn(quizSet);
    }

    @Test
    @DisplayName("퀴즈 풀이 시작 성공")
    void startQuiz_Success() {
        given(quizSetRepository.findById(100L)).willReturn(Optional.of(quizSet));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        Long attemptId = quizSolveService.startQuiz(100L, 1L);
        
        verify(quizAttemptRepository).save(any(QuizAttempt.class));
    }

    @Test
    @DisplayName("답안 정상 제출 성공")
    void submitAnswers_Success() {
        given(quizAttemptRepository.findById(200L)).willReturn(Optional.of(quizAttempt));
        given(quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(quizSet)).willReturn(List.of(question1, question2));
        given(quizChoiceRepository.findById(50L)).willReturn(Optional.of(choice1));

        QuizSubmitRequest request = new QuizSubmitRequest();
        QuizSubmitRequest.AnswerRequest ans1 = new QuizSubmitRequest.AnswerRequest();
        ReflectionTestUtils.setField(ans1, "questionId", 10L);
        ReflectionTestUtils.setField(ans1, "submittedChoiceId", 50L);

        QuizSubmitRequest.AnswerRequest ans2 = new QuizSubmitRequest.AnswerRequest();
        ReflectionTestUtils.setField(ans2, "questionId", 11L);
        ReflectionTestUtils.setField(ans2, "submittedAnswerText", "스프링 프레임워크");

        ReflectionTestUtils.setField(request, "answers", List.of(ans1, ans2));

        lenient().when(quizAttempt.getSubmittedAt()).thenReturn(java.time.LocalDateTime.now());
        QuizSubmitResponse response = quizSolveService.submitAnswers(200L, 1L, request);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getCorrectCount()).isEqualTo(2); // 둘 다 정답 처리됨 (Mocking 로직)
        verify(quizAnswerRepository, times(2)).save(any(QuizAnswer.class));
        verify(quizAttempt).submit();
        verify(quizResultRepository).save(any(QuizResult.class));
    }

    @Test
    @DisplayName("답안 누락 시 예외 발생")
    void submitAnswers_MissingAnswer() {
        given(quizAttemptRepository.findById(200L)).willReturn(Optional.of(quizAttempt));
        given(quizQuestionRepository.findByQuizSetOrderByOrderNoAsc(quizSet)).willReturn(List.of(question1, question2));

        QuizSubmitRequest request = new QuizSubmitRequest();
        QuizSubmitRequest.AnswerRequest ans1 = new QuizSubmitRequest.AnswerRequest();
        ReflectionTestUtils.setField(ans1, "questionId", 10L);
        ReflectionTestUtils.setField(ans1, "submittedChoiceId", 50L);

        // 1개만 제출
        ReflectionTestUtils.setField(request, "answers", List.of(ans1));

        assertThatThrownBy(() -> quizSolveService.submitAnswers(200L, 1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("답안의 개수가 퀴즈 세트의 문제 개수와 일치하지 않습니다");
    }
}
