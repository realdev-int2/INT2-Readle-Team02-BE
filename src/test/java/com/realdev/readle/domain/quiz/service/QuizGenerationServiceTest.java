package com.realdev.readle.domain.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.quiz.dto.response.QuizCreateResponse;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.domain.quiz.repository.QuizChoiceRepository;
import com.realdev.readle.domain.quiz.repository.QuizQuestionRepository;
import com.realdev.readle.domain.quiz.repository.QuizSetRepository;
import com.realdev.readle.global.exception.CustomException;
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
  @Mock private com.realdev.readle.domain.tag.service.TagService tagService;

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
            tagService,
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

    org.mockito.BDDMockito.lenient()
        .when(transactionTemplate.execute(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              org.springframework.transaction.support.TransactionCallback callback =
                  invocation.getArgument(0);
              if (callback == null) return null;
              return callback.doInTransaction(null);
            });
  }

  @Test
  @DisplayName("정상 검증 통과본에 대해 퀴즈 생성 성공")
  void createQuizSet_Success() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

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
    org.mockito.Mockito.verify(tagService, org.mockito.Mockito.times(1))
        .saveContentTags(any(), any());
  }

  @Test
  @DisplayName("기존에 FAILED 상태인 퀴즈 세트가 있으면 retry()를 호출하여 로우를 재활용한다")
  void createQuizSet_ReusesFailedQuizSet() {
    lenient().when(validation.getStatus()).thenReturn(ValidationStatus.PASSED);
    lenient()
        .when(contentValidationRepository.findByIdWithContent(100L))
        .thenReturn(Optional.of(validation));

    // 기존 FAILED 상태의 QuizSet 모킹
    QuizSet existingQuizSet = org.mockito.Mockito.spy(QuizSet.create(content, validation, false));
    existingQuizSet.fail(); // FAILED 상태로 만듦
    ReflectionTestUtils.setField(existingQuizSet, "id", 300L);

    // 기존 QuizSet 반환하도록 모킹
    given(quizSetRepository.findBySourceValidationId(100L))
        .willReturn(Optional.of(existingQuizSet));
    given(quizSetRepository.saveAndFlush(existingQuizSet)).willReturn(existingQuizSet);
    given(quizSetRepository.findById(300L)).willReturn(Optional.of(existingQuizSet));

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
    assertThat(response.getQuizId()).isEqualTo(300L); // 기존 ID 재활용 검증
    assertThat(response.getStatus()).isEqualTo("completed");

    // retry()가 불려서 GENERATING을 거쳤는지 검증
    org.mockito.Mockito.verify(existingQuizSet, org.mockito.Mockito.times(1)).retry();

    // delete는 절대 호출되지 않아야 함
    org.mockito.Mockito.verify(quizSetRepository, org.mockito.Mockito.never()).delete(any());
  }

  @Test
  @DisplayName("기존에 FAILED 상태인 퀴즈 세트 재시도 중 AI 호출에 실패하면, retry() 후 다시 FAILED로 상태가 복구된다")
  void createQuizSet_ReusesFailedQuizSet_AndFailsAgain() {
    lenient().when(validation.getStatus()).thenReturn(ValidationStatus.PASSED);
    lenient()
        .when(contentValidationRepository.findByIdWithContent(100L))
        .thenReturn(Optional.of(validation));

    // 기존 FAILED 상태의 QuizSet 모킹
    QuizSet existingQuizSet = org.mockito.Mockito.spy(QuizSet.create(content, validation, false));
    existingQuizSet.fail(); // FAILED 상태로 만듦
    ReflectionTestUtils.setField(existingQuizSet, "id", 300L);
    ReflectionTestUtils.setField(existingQuizSet, "questionCount", 3);
    ReflectionTestUtils.setField(
        existingQuizSet, "completedAt", java.time.LocalDateTime.now().minusDays(1));

    // 기존 QuizSet 반환하도록 모킹
    given(quizSetRepository.findBySourceValidationId(100L))
        .willReturn(Optional.of(existingQuizSet));
    given(quizSetRepository.saveAndFlush(existingQuizSet))
        .willAnswer(
            invocation -> {
              QuizSet arg = invocation.getArgument(0);
              assertThat(arg.getStatus())
                  .isEqualTo(com.realdev.readle.domain.quiz.entity.QuizSetStatus.GENERATING);
              assertThat(arg.getQuestionCount()).isNull();
              assertThat(arg.getCompletedAt()).isNull();
              return arg;
            });
    given(quizSetRepository.findById(300L)).willReturn(Optional.of(existingQuizSet));

    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    // AI API 호출 시 예외를 던지도록 모킹 (재시도 중 실패 상황)
    given(claudeClient.getGeneratedText(anyString(), anyString()))
        .willThrow(new CustomException(QuizErrorCode.QUIZ_GENERATION_FAILED, "AI 응답 지연"));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);

    // retry()가 불려서 GENERATING으로 전환되었는지 검증
    org.mockito.Mockito.verify(existingQuizSet, org.mockito.Mockito.times(1)).retry();

    // saveAndFlush 호출 횟수 검증 (내부 상태 검증은 given의 willAnswer에서 수행됨)
    org.mockito.Mockito.verify(quizSetRepository, org.mockito.Mockito.times(1))
        .saveAndFlush(any(QuizSet.class));

    // catch 블록에서 fail()이 호출되어 다시 FAILED로 돌아왔는지 검증
    // 초기 셋업 때 fail() 1번 + catch 블록에서 fail() 1번 = 총 2번 호출됨
    org.mockito.Mockito.verify(existingQuizSet, org.mockito.Mockito.times(2)).fail();

    // 최종 상태 검증
    assertThat(existingQuizSet.getStatus())
        .isEqualTo(com.realdev.readle.domain.quiz.entity.QuizSetStatus.FAILED);

    // 실패 후 복구 시 save가 호출되었는지 검증
    org.mockito.Mockito.verify(quizSetRepository, org.mockito.Mockito.times(1))
        .save(existingQuizSet);

    // delete는 호출되지 않아야 함
    org.mockito.Mockito.verify(quizSetRepository, org.mockito.Mockito.never()).delete(any());
  }

  @Test
  @DisplayName("PENDING 상태의 검증본은 퀴즈 생성 불가 예외 발생")
  void createQuizSet_ThrowsWhenPending() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PENDING);
    org.mockito.BDDMockito.lenient()
        .when(validation.getValidationMethod())
        .thenReturn(ValidationMethod.STATIC_GUARDRAIL);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.VALIDATION_NOT_PASSED);
  }

  @Test
  @DisplayName("FAILED 상태의 검증본은 퀴즈 생성 불가 예외 발생")
  void createQuizSet_ThrowsWhenFailed() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.FAILED);
    org.mockito.BDDMockito.lenient()
        .when(validation.getValidationMethod())
        .thenReturn(ValidationMethod.STATIC_GUARDRAIL);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.VALIDATION_NOT_PASSED);
  }

  @Test
  @DisplayName("본문 텍스트가 비어 있으면 예외 발생")
  void createQuizSet_ThrowsWhenContentTextIsBlank() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));
    org.mockito.BDDMockito.lenient().when(content.getRawText()).thenReturn("   ");
    org.mockito.BDDMockito.lenient().when(content.getExtractedText()).thenReturn("   ");

    given(quizSetRepository.findBySourceValidationId(100L)).willReturn(Optional.empty());

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.EMPTY_SOURCE_TEXT_FOR_QUIZ);

    // 예외 보상 상태 전이 검증 (FAILED 상태 전이 확인)
    assertThat(expectedQuizSet.getStatus())
        .isEqualTo(com.realdev.readle.domain.quiz.entity.QuizSetStatus.FAILED);
  }

  @Test
  @DisplayName("태그 개수가 3개 치과하면 QuizGenerationException 발생")
  void createQuizSet_ThrowsWhenTagCountExceeded() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));
    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    String claudeJsonResponse =
        """
            {
              "tags": ["A", "B", "C", "D"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "multiple_choice",
                  "question": "Q?",
                  "options": ["A", "B"],
                  "code_snippet": null,
                  "answer": "0"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when & then: parseAndValidate에서 CustomException 발생 ->
    // catch에 잡혀 wrap됨
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);
  }

  @Test
  @DisplayName("객관식 정답이 0개이면 QuizGenerationException 발생")
  void createQuizSet_ThrowsWhenNoCorrectAnswer() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));
    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    // answer를 "99"로 설정해 정답 선택지가 0개가 되도록 유도
    String claudeJsonResponse =
        """
            {
              "tags": ["Test"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "multiple_choice",
                  "question": "Q?",
                  "options": ["A", "B"],
                  "code_snippet": null,
                  "answer": "99"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when & then: 문제 저장 트랜잭션 내에서 CustomException 발생 ->
    // catch에 잡혀 wrap됨
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);
  }

  @Test
  @DisplayName("객관식 문제의 선택지가 비어있으면 QuizGenerationException 발생")
  void createQuizSet_ThrowsWhenMultipleChoiceOptionsEmpty() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));
    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    // options를 empty array로 제공
    String claudeJsonResponse =
        """
            {
              "tags": ["Test"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "multiple_choice",
                  "question": "Q?",
                  "options": [],
                  "code_snippet": null,
                  "answer": "0"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);
  }

  @Test
  @DisplayName("알 수 없는 type 값이면 QuizGenerationException 발생")
  void createQuizSet_ThrowsWhenUnknownType() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

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
                  "type": "essay",
                  "question": "Q?",
                  "options": null,
                  "code_snippet": null,
                  "answer": "some answer"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when & then: 문제 저장 트랜잭션 내에서 CustomException 발생 ->
    // catch에 잡혀 wrap됨
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);
  }

  @Test
  @DisplayName("코드 없는 본문에 AI가 CODE_BLANK만 반환하면 생성된 문제 0개로 실패 처리")
  void createQuizSet_ThrowsWhenGeneratedQuestionCountIsZero() {
    // given
    given(validation.getStatus()).willReturn(ValidationStatus.PASSED);
    given(contentValidationRepository.findByIdWithContent(100L))
        .willReturn(Optional.of(validation));

    // 코드가 없는 본문으로 설정 (hasCode = false)
    org.mockito.BDDMockito.lenient()
        .when(content.getRawText())
        .thenReturn("이것은 코드가 전혀 없는 순수 텍스트 본문입니다.");

    QuizSet expectedQuizSet = QuizSet.create(content, validation, false);
    ReflectionTestUtils.setField(expectedQuizSet, "id", 200L);
    given(quizSetRepository.saveAndFlush(any(QuizSet.class))).willReturn(expectedQuizSet);
    given(quizSetRepository.findById(200L)).willReturn(Optional.of(expectedQuizSet));
    given(promptLoader.loadPrompt(anyString(), any())).willReturn("system prompt");

    // AI가 CODE_BLANK 하나만 반환하도록 설정
    String claudeJsonResponse =
        """
            {
              "tags": ["Test"],
              "quizzes": [
                {
                  "id": 1,
                  "type": "code_blank",
                  "question": "Q?",
                  "options": null,
                  "code_snippet": "test",
                  "answer": "answer"
                }
              ]
            }
            """;
    given(claudeClient.getGeneratedText(anyString(), anyString())).willReturn(claudeJsonResponse);

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.QUIZ_GENERATION_FAILED);

    // 실패 상태 전이 확인
    assertThat(expectedQuizSet.getStatus())
        .isEqualTo(com.realdev.readle.domain.quiz.entity.QuizSetStatus.FAILED);

    // 태그 저장이 수행되지 않았음을 확인
    org.mockito.Mockito.verify(tagService, org.mockito.Mockito.never())
        .saveContentTags(any(), any());
  }

  // =========================================================================
  // 6. Validation 및 Bypass(우회) 분기 회귀 테스트
  // =========================================================================

  @Test
  @DisplayName("AI 검증 REJECTED인 경우 우회 생성이 허용되고 isBypassed가 true로 설정된다")
  void createQuizSet_AllowsBypass_WhenAiRejected() {
    // given
    lenient().when(validation.getStatus()).thenReturn(ValidationStatus.REJECTED);
    lenient().when(validation.getValidationMethod()).thenReturn(ValidationMethod.AI);
    lenient()
        .when(contentValidationRepository.findByIdWithContent(100L))
        .thenReturn(Optional.of(validation));

    given(quizSetRepository.findBySourceValidationId(100L)).willReturn(Optional.empty());

    // saveAndFlush 될 때 isBypassed가 true인지 검증
    given(quizSetRepository.saveAndFlush(any(QuizSet.class)))
        .willAnswer(
            invocation -> {
              QuizSet arg = invocation.getArgument(0);
              assertThat(arg.getIsBypassed()).isTrue();
              ReflectionTestUtils.setField(arg, "id", 200L);
              return arg;
            });
    lenient()
        .when(quizSetRepository.findById(200L))
        .thenAnswer(
            invocation -> {
              QuizSet mockSet = QuizSet.create(validation.getContent(), validation, true);
              ReflectionTestUtils.setField(mockSet, "id", 200L);
              return Optional.of(mockSet);
            });

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
  }

  @Test
  @DisplayName("STATIC_GUARDRAIL 검증 REJECTED인 경우 우회 생성이 불가하며 예외가 발생한다")
  void createQuizSet_ThrowsException_WhenStaticGuardrailRejected() {
    // given
    lenient().when(validation.getStatus()).thenReturn(ValidationStatus.REJECTED);
    lenient().when(validation.getValidationMethod()).thenReturn(ValidationMethod.STATIC_GUARDRAIL);
    lenient()
        .when(contentValidationRepository.findByIdWithContent(100L))
        .thenReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.VALIDATION_NOT_PASSED);
  }

  @Test
  @DisplayName("AI 검증 방식이어도 ValidationStatus가 FAILED인 경우 우회 생성이 불가하며 예외가 발생한다")
  void createQuizSet_ThrowsException_WhenAiFailed() {
    // given
    lenient().when(validation.getStatus()).thenReturn(ValidationStatus.FAILED);
    lenient().when(validation.getValidationMethod()).thenReturn(ValidationMethod.AI);
    lenient()
        .when(contentValidationRepository.findByIdWithContent(100L))
        .thenReturn(Optional.of(validation));

    // when & then
    assertThatThrownBy(() -> quizGenerationService.createQuizSet(100L))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(QuizErrorCode.VALIDATION_NOT_PASSED);
  }
}
