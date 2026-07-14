package com.realdev.readle.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.quiz.dto.QuizCreateResponse;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.exception.ValidationNotPassedException;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.global.infrastructure.ai.ClaudeClient;
import com.realdev.readle.global.infrastructure.prompt.PromptLoader;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceTest {

  @InjectMocks private QuizGenerationService quizGenerationService;

  @Mock private ContentValidationRepository contentValidationRepository;
  @Mock private QuizSetRepository quizSetRepository;
  @Mock private QuizQuestionRepository quizQuestionRepository;
  @Mock private QuizChoiceRepository quizChoiceRepository;
  @Mock private ClaudeClient claudeClient;
  @Mock private PromptLoader promptLoader;
  @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

  private ObjectMapper objectMapper = new ObjectMapper();

  private ContentValidation validation;
  private Content content;
  private Member member;

  @BeforeEach
  void setUp() {
    quizGenerationService =
        new QuizGenerationService(
            contentValidationRepository,
            quizSetRepository,
            quizQuestionRepository,
            quizChoiceRepository,
            claudeClient,
            promptLoader,
            objectMapper,
            transactionTemplate);

    member = org.mockito.Mockito.mock(Member.class);
    ReflectionTestUtils.setField(member, "id", 1L);

    content = org.mockito.Mockito.mock(Content.class);
    ReflectionTestUtils.setField(content, "id", 1L);
    org.mockito.BDDMockito.lenient()
        .when(content.getRawText())
        .thenReturn("This is test content with public void someCode() {}");

    validation = org.mockito.Mockito.mock(ContentValidation.class);
    ReflectionTestUtils.setField(validation, "id", 100L);
    org.mockito.BDDMockito.lenient().when(validation.getContent()).thenReturn(content);
  }

  @Test
  @DisplayName("정상 검증 통과본에 대해 퀴즈 생성 성공")
  void createQuizSet_Success() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findById(100L)).willReturn(Optional.of(validation));

    given(transactionTemplate.execute(any()))
        .willAnswer(
            invocation -> {
              org.springframework.transaction.support.TransactionCallback callback =
                  invocation.getArgument(0);
              return callback.doInTransaction(null);
            });

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));

    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    String claudeJsonResponse =
        """
            {
              "tags": ["Test"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "multiple_choice",
                  "question": "Test Q?",
                  "options": ["A", "B"],
                  "code_snippet": null,
                  "answer": "0"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when
    QuizCreateResponse response = quizGenerationService.createQuizSet(100L);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getQuizId()).isEqualTo(200L);
    assertThat(response.getQuestionCount()).isEqualTo(1);
    assertThat(response.getStatus()).isEqualTo("completed");

    org.mockito.Mockito.verify(quizQuestionRepository, org.mockito.Mockito.times(1)).save(any());
    org.mockito.Mockito.verify(quizChoiceRepository, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  @DisplayName("PENDING 상태의 검증본은 퀴즈 생성 불가 예외 발생")
  void createQuizSet_ThrowsWhenPending() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PENDING);
    given(contentValidationRepository.findById(100L)).willReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(ValidationNotPassedException.class)
        .hasMessageContaining("생성이 불가능한 상태입니다");
  }

  @Test
  @DisplayName("FAILED 상태의 검증본은 퀴즈 생성 불가 예외 발생")
  void createQuizSet_ThrowsWhenFailed() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.FAILED);
    given(contentValidationRepository.findById(100L)).willReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(ValidationNotPassedException.class)
        .hasMessageContaining("생성이 불가능한 상태입니다");
  }

  @Test
  @DisplayName("본문 텍스트가 비어 있으면 예외 발생")
  void createQuizSet_ThrowsWhenContentTextIsBlank() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findById(100L)).willReturn(Optional.of(validation));
    org.mockito.BDDMockito.given(content.getRawText()).willReturn("   ");

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("퀴즈를 생성할 본문 텍스트가 존재하지 않습니다.");
  }
}
