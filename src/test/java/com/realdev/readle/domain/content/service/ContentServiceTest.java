package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.dto.response.ContentValidationResponse;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import com.realdev.readle.domain.content.entity.CrawlStatus;
import com.realdev.readle.domain.content.entity.ErrorCode;
import com.realdev.readle.domain.content.entity.InputType;
import com.realdev.readle.domain.content.entity.RejectReasonCode;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.exception.MemberErrorCode;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

  @Mock private ContentRepository contentRepository;
  @Mock private ContentValidationRepository contentValidationRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private ContentService contentService;

  @Test
  @DisplayName("memberUuid가 null이면 DB 조회 없이 UNAUTHORIZED 예외가 발생한다")
  void createContent_nullMemberUuid_throwsUnauthorized() {
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문");

    assertThatThrownBy(() -> contentService.createContent(request, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("UUID에 해당하는 회원이 없으면 MEMBER_NOT_FOUND 예외가 발생한다")
  void createContent_memberNotFound_throwsMemberNotFound() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문");

    when(memberRepository.findByUuid(memberUuid)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
  }

  @Test
  @DisplayName("텍스트가 15,000자를 초과하면 DB 조회 없이 CONTENT_TOO_LARGE 예외가 발생한다")
  void createContent_textTooLarge_throwsBeforeDbQuery() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, null, "가".repeat(15_001));

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_TOO_LARGE);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("텍스트가 정확히 15,000자이면 예외 없이 정상 저장된다")
  void createContent_textExactly15000_success() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    String text = "가".repeat(15_000);
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, text);
    Content savedContent = stubSave(Content.fromText(mockMember, "제목", text), 1L);

    when(memberRepository.findByUuid(memberUuid)).thenReturn(Optional.of(mockMember));
    when(contentRepository.save(any(Content.class))).thenReturn(savedContent);

    ContentCreateResponse response = contentService.createContent(request, memberUuid);

    assertThat(response.contentId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("inputType=URL일 때 text 필드가 주어지면 UNNECESSARY_TEXT 예외가 발생한다")
  void createContent_urlType_withText_throwsUnnecessaryText() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(
            InputType.URL, "제목", "https://example.com", "정상적인 추출 본문", "본문 텍스트");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.UNNECESSARY_TEXT);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT이고 title이 null이면 text 앞 30자가 제목으로 저장된다")
  void createContent_textType_nullTitle_useFirst30Chars() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    String text = "가".repeat(50);
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, null, null, null, text);

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getTitle()).isEqualTo("가".repeat(30));
  }

  @Test
  @DisplayName("inputType=TEXT이고 title이 공백이면 text 앞 30자가 제목으로 저장된다")
  void createContent_textType_blankTitle_useFirst30Chars() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    String text = "나".repeat(50);
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "   ", null, null, text);

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getTitle()).isEqualTo("나".repeat(30));
  }

  @Test
  @DisplayName("inputType=TEXT이고 text가 30자 미만이면 text 전체가 제목으로 저장된다")
  void createContent_textType_blankTitle_textShorterThan30Chars() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    String text = "짧은 본문";
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "", null, null, text);

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getTitle()).isEqualTo("짧은 본문");
  }

  @Test
  @DisplayName("inputType=TEXT이고 title이 주어지면 제공된 title이 그대로 저장된다")
  void createContent_textType_titleProvided_usesProvidedTitle() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "직접 입력한 제목", null, null, "본문 내용");

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getTitle()).isEqualTo("직접 입력한 제목");
  }

  @Test
  @DisplayName("inputType=TEXT이면 crawlStatus=NOT_APPLICABLE, rawText가 저장되고 originalUrl은 null이다")
  void createContent_textType_mapsCorrectly() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문 내용");

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getInputType()).isEqualTo(InputType.TEXT);
    assertThat(captured.getCrawlStatus()).isEqualTo(CrawlStatus.NOT_APPLICABLE);
    assertThat(captured.getRawText()).isEqualTo("본문 내용");
    assertThat(captured.getOriginalUrl()).isNull();
    assertThat(captured.getExtractedText()).isNull();
  }

  @Test
  @DisplayName("inputType=URL이면 crawlStatus=SUCCESS, originalUrl과 extractedText가 저장된다")
  void createContent_urlType_mapsCorrectly() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https://example.com", "추출된 본문", null);

    setupMemberAndCaptureContent(memberUuid, mockMember);

    contentService.createContent(request, memberUuid);

    Content captured = captureContent();
    assertThat(captured.getInputType()).isEqualTo(InputType.URL);
    assertThat(captured.getCrawlStatus()).isEqualTo(CrawlStatus.SUCCESS);
    assertThat(captured.getOriginalUrl()).isEqualTo("https://example.com");
    assertThat(captured.getExtractedText()).isEqualTo("추출된 본문");
    assertThat(captured.getRawText()).isNull();
  }

  @Test
  @DisplayName("정상 저장 후 contentId와 validationStatus=PENDING이 반환된다")
  void createContent_returnsPendingStatus() {
    String memberUuid = UUID.randomUUID().toString();
    Member mockMember = mockMember();
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문");
    Content savedContent = stubSave(Content.fromText(mockMember, "제목", "본문"), 42L);

    when(memberRepository.findByUuid(memberUuid)).thenReturn(Optional.of(mockMember));
    when(contentRepository.save(any(Content.class))).thenReturn(savedContent);

    ContentCreateResponse response = contentService.createContent(request, memberUuid);

    assertThat(response.contentId()).isEqualTo(42L);
    assertThat(response.validationStatus()).isEqualTo(ValidationStatus.PENDING);
    verify(eventPublisher).publishEvent(new ContentCreatedEvent(42L, memberUuid));
  }

  @Test
  @DisplayName("inputType=URL이고 url이 null이면 URL_REQUIRED 예외가 발생한다")
  void createContent_urlType_nullUrl_throwsUrlRequired() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", null, "추출된 본문", null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.URL_REQUIRED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT이고 text가 null이면 TEXT_REQUIRED 예외가 발생한다")
  void createContent_textType_nullText_throwsTextRequired() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TEXT_REQUIRED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT이고 text가 빈 문자열이면 TEXT_REQUIRED 예외가 발생한다")
  void createContent_textType_emptyText_throwsTextRequired() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TEXT_REQUIRED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT이고 text가 공백 문자열이면 TEXT_REQUIRED 예외가 발생한다")
  void createContent_textType_blankText_throwsTextRequired() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, null, "   ");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TEXT_REQUIRED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL이고 extractedText가 null이면 MISSING_EXTRACTED_TEXT 예외가 발생한다")
  void createContent_urlType_nullExtractedText_throwsMissingExtractedText() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https://example.com", null, null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.MISSING_EXTRACTED_TEXT);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL이고 extractedText가 15,000자를 초과하면 CONTENT_TOO_LARGE 예외가 발생한다")
  void createContent_urlType_extractedTextTooLarge_throwsContentTooLarge() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(
            InputType.URL, "제목", "https://example.com", "가".repeat(15_001), null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_TOO_LARGE);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT일 때 url 필드가 주어지면 UNNECESSARY_URL_INFO 예외가 발생한다")
  void createContent_textType_withUrl_throwsUnnecessaryUrlInfo() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", "https://example.com", null, "본문 내용");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.UNNECESSARY_URL_INFO);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT일 때 extractedText 필드가 주어지면 UNNECESSARY_URL_INFO 예외가 발생한다")
  void createContent_textType_withExtractedText_throwsUnnecessaryUrlInfo() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, "추출된 본문", "본문 내용");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.UNNECESSARY_URL_INFO);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL일 때 title 필드가 누락되면 TITLE_REQUIRED 예외가 발생한다")
  void createContent_urlType_nullTitle_throwsTitleRequired() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, null, "https://example.com", "본문", null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TITLE_REQUIRED);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL일 때 title 필드가 255자를 초과하면 TITLE_TOO_LONG 예외가 발생한다")
  void createContent_urlType_titleTooLong_throwsTitleTooLong() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "가".repeat(256), "https://example.com", "본문", null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TITLE_TOO_LONG);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=TEXT일 때 title 필드가 255자를 초과하면 TITLE_TOO_LONG 예외가 발생한다")
  void createContent_textType_titleTooLong_throwsTitleTooLong() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "가".repeat(256), null, null, "본문");

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.TITLE_TOO_LONG);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL일 때 잘못된 스킴의 URL 형식이면 INVALID_URL 예외가 발생한다")
  void createContent_urlType_invalidUrlFormat_throwsInvalidUrl() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "ftp://example.com", "본문", null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType=URL일 때 호스트가 누락된 URL 형식이면 INVALID_URL 예외가 발생한다")
  void createContent_urlType_missingHost_throwsInvalidUrl() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https:example.com", "본문", null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    verify(memberRepository, never()).findByUuid(any());
  }

  @Test
  @DisplayName("inputType이 null이면 INVALID_INPUT_TYPE 예외가 발생한다")
  void createContent_nullInputType_throwsInvalidInputType() {
    String memberUuid = UUID.randomUUID().toString();
    ContentCreateRequest request = new ContentCreateRequest(null, "제목", null, null, null);

    assertThatThrownBy(() -> contentService.createContent(request, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_INPUT_TYPE);

    verify(memberRepository, never()).findByUuid(any());
  }

  private final ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);

  private Member mockMember() {
    return org.mockito.Mockito.mock(Member.class);
  }

  private Content stubSave(Content content, Long id) {
    ReflectionTestUtils.setField(content, "id", id);
    return content;
  }

  private void setupMemberAndCaptureContent(String memberUuid, Member mockMember) {
    when(memberRepository.findByUuid(memberUuid)).thenReturn(Optional.of(mockMember));
    when(contentRepository.save(contentCaptor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Content captureContent() {
    return contentCaptor.getValue();
  }

  private Content mockOwnedContent(String memberUuid) {
    Member owner = org.mockito.Mockito.mock(Member.class);
    when(owner.getUuid()).thenReturn(memberUuid);
    Content content = org.mockito.Mockito.mock(Content.class);
    when(content.getMember()).thenReturn(owner);
    return content;
  }

  @Test
  @DisplayName("memberUuid가 null이면 DB 조회 없이 UNAUTHORIZED 예외가 발생한다")
  void getValidationResult_nullMemberUuid_throwsUnauthorized() {
    assertThatThrownBy(() -> contentService.getValidationResult(1L, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

    verify(contentRepository, never()).findById(any());
  }

  @Test
  @DisplayName("존재하지 않는 콘텐츠 조회 시 CONTENT_NOT_FOUND 예외가 발생한다")
  void getValidationResult_contentNotFound() {
    String memberUuid = UUID.randomUUID().toString();
    when(contentRepository.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.getValidationResult(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }

  @Test
  @DisplayName("타인의 콘텐츠 조회 시 CONTENT_ACCESS_DENIED 예외가 발생한다")
  void getValidationResult_forbidden() {
    String requesterUuid = UUID.randomUUID().toString();
    String ownerUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(ownerUuid);

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));

    assertThatThrownBy(() -> contentService.getValidationResult(1L, requesterUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_ACCESS_DENIED);
  }

  @Test
  @DisplayName("검증 이력이 없는 경우 CONTENT_VALIDATION_NOT_FOUND 예외가 발생한다")
  void getValidationResult_validationNotFound() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.getValidationResult(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_VALIDATION_NOT_FOUND);
  }

  @Test
  @DisplayName("PENDING 상태인 경우 validatedAt은 null로 반환된다")
  void getValidationResult_pendingState() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    ContentValidation validation =
        ContentValidation.builder()
            .status(ValidationStatus.PENDING)
            .validationMethod(ValidationMethod.AI)
            .validatedAt(LocalDateTime.now()) // 강제로 세팅해도 null이어야 함
            .build();

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.getValidationResult(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.PENDING);
    assertThat(response.validatedAt()).isNull();
    assertThat(response.bypassAvailable()).isFalse();
  }

  @Test
  @DisplayName("PASSED 상태인 경우 validatedAt이 정상적으로 반환된다")
  void getValidationResult_passedState() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    LocalDateTime validatedAt = LocalDateTime.of(2026, 7, 20, 10, 0);
    ContentValidation validation =
        ContentValidation.builder()
            .status(ValidationStatus.PASSED)
            .validationMethod(ValidationMethod.AI)
            .validationScore(new java.math.BigDecimal("95.5"))
            .validatedAt(validatedAt)
            .build();

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.getValidationResult(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.PASSED);
    assertThat(response.validatedAt()).isEqualTo(validatedAt);
    assertThat(response.bypassAvailable()).isFalse();
  }

  @Test
  @DisplayName("REJECTED + AI 방식인 경우 bypassAvailable은 true를 반환한다")
  void getValidationResult_rejectedAiState() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    ContentValidation validation =
        ContentValidation.builder()
            .status(ValidationStatus.REJECTED)
            .validationMethod(ValidationMethod.AI)
            .rejectReasonCode(RejectReasonCode.CONTENT_TOO_SHORT)
            .validatedAt(LocalDateTime.now())
            .build();

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.getValidationResult(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(response.bypassAvailable()).isTrue();
    assertThat(response.errorCode()).isEqualTo("CONTENT_TOO_SHORT");
    assertThat(response.message()).isEqualTo("분석하기에 텍스트 내용이 너무 짧습니다. 충분한 길이의 텍스트를 입력해 주세요.");
  }

  @Test
  @DisplayName("REJECTED + STATIC_GUARDRAIL 방식인 경우 bypassAvailable은 false를 반환하고 에러 메시지를 매핑한다")
  void getValidationResult_rejectedNonAiState() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    ContentValidation validation =
        ContentValidation.builder()
            .status(ValidationStatus.REJECTED)
            .validationMethod(ValidationMethod.STATIC_GUARDRAIL)
            .rejectReasonCode(RejectReasonCode.BAD_WORD)
            .validatedAt(LocalDateTime.now())
            .build();

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.getValidationResult(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.REJECTED);
    assertThat(response.bypassAvailable()).isFalse();
    assertThat(response.errorCode()).isEqualTo("BAD_WORD");
    assertThat(response.message())
        .isEqualTo("비속어 또는 서비스 정책상 부적절한 표현이 포함되어 있습니다. 내용을 수정한 후 다시 등록해 주세요.");
  }

  @Test
  @DisplayName("FAILED 상태인 경우 errorCode와 message가 정확히 매핑된다")
  void getValidationResult_failedState() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    ContentValidation validation =
        ContentValidation.builder()
            .status(ValidationStatus.FAILED)
            .validationMethod(ValidationMethod.AI)
            .errorCode(ErrorCode.AI_SERVICE_ERROR)
            .validatedAt(LocalDateTime.now())
            .build();

    when(contentRepository.findById(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.getValidationResult(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.FAILED);
    assertThat(response.bypassAvailable()).isFalse();
    assertThat(response.errorCode()).isEqualTo("AI_SERVICE_ERROR");
    assertThat(response.message()).isEqualTo("AI 검증 서비스에 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
  }

  @Test
  @DisplayName("재시도 시 memberUuid가 null이면 UNAUTHORIZED 예외가 발생한다")
  void retryValidation_nullMemberUuid_throwsUnauthorized() {
    assertThatThrownBy(() -> contentService.retryValidation(1L, null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
  }

  @Test
  @DisplayName("재시도 시 콘텐츠가 없으면 CONTENT_NOT_FOUND 예외가 발생한다")
  void retryValidation_contentNotFound() {
    String memberUuid = UUID.randomUUID().toString();
    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.retryValidation(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_NOT_FOUND);
  }

  @Test
  @DisplayName("타인의 콘텐츠를 재시도하면 CONTENT_ACCESS_DENIED 예외가 발생한다")
  void retryValidation_forbidden() {
    String requesterUuid = UUID.randomUUID().toString();
    String ownerUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(ownerUuid);

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));

    assertThatThrownBy(() -> contentService.retryValidation(1L, requesterUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_ACCESS_DENIED);
  }

  @Test
  @DisplayName("재시도 시 검증 이력이 없으면 CONTENT_VALIDATION_NOT_FOUND 예외가 발생한다")
  void retryValidation_validationNotFound() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> contentService.retryValidation(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.CONTENT_VALIDATION_NOT_FOUND);
  }

  @Test
  @DisplayName("재시도 시 상태가 PENDING이면 VALIDATION_ALREADY_RUNNING 예외가 발생한다")
  void retryValidation_pending_throwsConflict() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);
    ContentValidation validation =
        ContentValidation.builder().status(ValidationStatus.PENDING).build();

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    assertThatThrownBy(() -> contentService.retryValidation(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.VALIDATION_ALREADY_RUNNING);
  }

  @Test
  @DisplayName("재시도 시 상태가 PASSED이면 NOT_RETRYABLE 예외가 발생한다")
  void retryValidation_passed_throwsNotRetryable() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);
    ContentValidation validation =
        ContentValidation.builder().status(ValidationStatus.PASSED).build();

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    assertThatThrownBy(() -> contentService.retryValidation(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.NOT_RETRYABLE);
  }

  @Test
  @DisplayName("재시도 시 상태가 REJECTED이면 NOT_RETRYABLE 예외가 발생한다")
  void retryValidation_rejected_throwsNotRetryable() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);
    ContentValidation validation =
        ContentValidation.builder().status(ValidationStatus.REJECTED).build();

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    assertThatThrownBy(() -> contentService.retryValidation(1L, memberUuid))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.NOT_RETRYABLE);
  }

  @Test
  @DisplayName("재시도 시 상태가 FAILED이면 이벤트를 재발행하고 PENDING을 반환한다")
  void retryValidation_failed_publishesEventAndReturnsPending() {
    String memberUuid = UUID.randomUUID().toString();
    Content content = mockOwnedContent(memberUuid);
    ReflectionTestUtils.setField(content, "id", 1L); // ContentCreatedEvent uses content id

    ContentValidation validation =
        ContentValidation.builder().status(ValidationStatus.FAILED).build();

    when(contentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(content));
    when(contentValidationRepository.findFirstByContentIdOrderByCreatedAtDesc(1L))
        .thenReturn(Optional.of(validation));

    ContentValidationResponse response = contentService.retryValidation(1L, memberUuid);

    assertThat(response.status()).isEqualTo(ValidationStatus.PENDING);
    verify(eventPublisher).publishEvent(new ContentCreatedEvent(1L, memberUuid));
  }
}
