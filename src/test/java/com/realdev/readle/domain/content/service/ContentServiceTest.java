package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.CrawlStatus;
import com.realdev.readle.domain.content.entity.InputType;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.exception.MemberErrorCode;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.util.crawler.WebCrawler;
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

  @Mock private WebCrawler webCrawler;
  @Mock private ContentRepository contentRepository;
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
}
