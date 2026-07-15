package com.realdev.readle.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.dto.request.ContentCreateRequest;
import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentCreateResponse;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.entity.InputType;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.service.ContentService;
import com.realdev.readle.domain.member.exception.MemberErrorCode;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "security.jwtIssuer=test-issuer",
      "security.jwtSecret=test-secret-test-secret-test-secret-32bytes",
      "security.jwtAudience=test-audience",
      "security.accessTokenMinutes=30",
      "security.refreshTokenDays=7",
      "security.stateEncryptionKey=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=", // gitleaks:allow
      // deterministic
      // OAuth state
      // encryption test
      // key
      "security.stateMinutes=5",
      "security.backendOrigin=http://localhost:8080"
    })
class ContentControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ContentService contentService;
  @MockitoBean private JwtService jwtService;

  @Test
  @DisplayName("정상적인 URL 요청 시 본문과 제목을 추출하여 200 OK를 반환한다")
  void extractSuccess() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentExtractRequest request = new ContentExtractRequest("https://example.com");
    ContentExtractResponse response = new ContentExtractResponse("추출된 제목", "추출된 마크다운 본문");

    when(contentService.extract(any(ContentExtractRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/api/contents/extract")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("추출된 제목"))
        .andExpect(jsonPath("$.content").value("추출된 마크다운 본문"));
  }

  @Test
  @DisplayName("요청 DTO의 URL이 빈 값일 경우 400 INVALID_INPUT 에러를 반환한다")
  void extractValidationFailure() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentExtractRequest request = new ContentExtractRequest("   ");

    mockMvc
        .perform(
            post("/api/contents/extract")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.error.message").value("url: URL은 필수 입력값입니다."));
  }

  @Test
  @DisplayName("잘못된 스킴의 URL이 전송되면 400 INVALID_URL 에러를 반환한다")
  void extractInvalidUrl() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentExtractRequest request = new ContentExtractRequest("invalid-url-scheme");

    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.INVALID_URL));

    mockMvc
        .perform(
            post("/api/contents/extract")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_URL"))
        .andExpect(jsonPath("$.error.message").value("올바르지 않은 URL 형식입니다."));
  }

  @Test
  @DisplayName("크롤링 타임아웃 발생 시 422 CRAWLING_TIMEOUT 에러를 반환한다")
  void extractTimeout() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentExtractRequest request = new ContentExtractRequest("https://timeout-url.com");

    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.CRAWLING_TIMEOUT));

    mockMvc
        .perform(
            post("/api/contents/extract")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("CRAWLING_TIMEOUT"))
        .andExpect(jsonPath("$.error.message").value("웹 페이지 요청 시간이 초과되었습니다."));
  }

  @Test
  @DisplayName("크롤링 연결 실패 또는 본문 정제 실패 시 422 EXTRACT_FAILED 에러를 반환한다")
  void extractFailed() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentExtractRequest request = new ContentExtractRequest("https://blocked-url.com");

    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.EXTRACT_FAILED));

    mockMvc
        .perform(
            post("/api/contents/extract")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("EXTRACT_FAILED"))
        .andExpect(jsonPath("$.error.message").value("콘텐츠 본문 추출에 실패했습니다. 본문 텍스트를 직접 입력해 주세요."));
  }

  @Test
  @DisplayName("inputType=TEXT로 인증된 요청 시 201 Created와 contentId, PENDING 상태를 반환한다")
  void createContent_textType_success() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문 내용입니다.");
    ContentCreateResponse response = new ContentCreateResponse(1L, ValidationStatus.PENDING);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenReturn(response);
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentId").value(1L))
        .andExpect(jsonPath("$.validationStatus").value("PENDING"));

    org.mockito.ArgumentCaptor<ContentCreateRequest> requestCaptor =
        org.mockito.ArgumentCaptor.forClass(ContentCreateRequest.class);
    verify(contentService).createContent(requestCaptor.capture(), eq(memberUuid));

    ContentCreateRequest captured = requestCaptor.getValue();
    assertThat(captured.inputType()).isEqualTo(InputType.TEXT);
    assertThat(captured.title()).isEqualTo("제목");
    assertThat(captured.url()).isNull();
    assertThat(captured.extractedText()).isNull();
    assertThat(captured.text()).isEqualTo("본문 내용입니다.");
  }

  @Test
  @DisplayName("inputType=URL로 인증된 요청 시 201 Created와 contentId, PENDING 상태를 반환한다")
  void createContent_urlType_success() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https://example.com", "추출된 본문", null);
    ContentCreateResponse response = new ContentCreateResponse(2L, ValidationStatus.PENDING);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenReturn(response);
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentId").value(2L))
        .andExpect(jsonPath("$.validationStatus").value("PENDING"));

    org.mockito.ArgumentCaptor<ContentCreateRequest> requestCaptor =
        org.mockito.ArgumentCaptor.forClass(ContentCreateRequest.class);
    verify(contentService).createContent(requestCaptor.capture(), eq(memberUuid));

    ContentCreateRequest captured = requestCaptor.getValue();
    assertThat(captured.inputType()).isEqualTo(InputType.URL);
    assertThat(captured.title()).isEqualTo("제목");
    assertThat(captured.url()).isEqualTo("https://example.com");
    assertThat(captured.extractedText()).isEqualTo("추출된 본문");
    assertThat(captured.text()).isNull();
  }

  @Test
  @DisplayName("미인증 요청 시 서비스가 401 UNAUTHORIZED 에러를 반환한다")
  void createContent_unauthorized() throws Exception {
    // 인증 없이 요청 → @AuthenticationPrincipal UUID는 null로 주입됨
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문");
    when(contentService.createContent(any(ContentCreateRequest.class), isNull()))
        .thenThrow(new CustomException(GlobalErrorCode.UNAUTHORIZED));
    mockMvc
        .perform(
            post("/api/contents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("inputType이 null이면 400 INVALID_INPUT 에러를 반환한다")
  void createContent_nullInputType() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    // inputType 필드를 JSON에서 누락
    String requestJson = "{\"title\":\"제목\",\"text\":\"본문\"}";
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  @DisplayName("텍스트가 15,000자를 초과하면 413 CONTENT_TOO_LARGE 에러를 반환한다")
  void createContent_contentTooLarge() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", null, null, "가".repeat(15_001));
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.CONTENT_TOO_LARGE));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error.code").value("CONTENT_TOO_LARGE"))
        .andExpect(jsonPath("$.error.message").value("텍스트가 15,000자를 초과합니다."));
  }

  @Test
  @DisplayName("UUID에 해당하는 회원이 없으면 404 MEMBER_NOT_FOUND 에러를 반환한다")
  void createContent_memberNotFound() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request = new ContentCreateRequest(InputType.TEXT, "제목", null, null, "본문");
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(MemberErrorCode.MEMBER_NOT_FOUND));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
  }

  @Test
  @DisplayName("URL 타입 등록 시 추출된 본문이 누락되면 400 MISSING_EXTRACTED_TEXT 에러를 반환한다")
  void createContent_missingExtractedText() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https://example.com", null, null);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.MISSING_EXTRACTED_TEXT));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("MISSING_EXTRACTED_TEXT"))
        .andExpect(
            jsonPath("$.error.message")
                .value("URL 본문 추출 결과가 누락되었습니다. 먼저 본문 추출을 완료한 후 등록을 요청해 주세요."));
  }

  @Test
  @DisplayName("URL 타입 등록 시 text 필드가 포함되어 있으면 400 UNNECESSARY_TEXT 에러를 반환한다")
  void createContent_unnecessaryText() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https://example.com", "추출된 본문", "텍스트");
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.UNNECESSARY_TEXT));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("UNNECESSARY_TEXT"))
        .andExpect(jsonPath("$.error.message").value("URL 입력 시 text 필드는 비어 있어야 합니다."));
  }

  @Test
  @DisplayName("TEXT 타입 등록 시 url 또는 extractedText 필드가 포함되어 있으면 400 UNNECESSARY_URL_INFO 에러를 반환한다")
  void createContent_unnecessaryUrlInfo() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "제목", "https://example.com", null, "텍스트");
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.UNNECESSARY_URL_INFO));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("UNNECESSARY_URL_INFO"))
        .andExpect(
            jsonPath("$.error.message").value("텍스트 입력 시 url 및 extractedText 필드는 비어 있어야 합니다."));
  }

  @Test
  @DisplayName("URL 타입 등록 시 제목이 누락되면 400 TITLE_REQUIRED 에러를 반환한다")
  void createContent_titleRequired() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, null, "https://example.com", "본문", null);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.TITLE_REQUIRED));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("TITLE_REQUIRED"))
        .andExpect(jsonPath("$.error.message").value("제목은 필수 입력값입니다."));
  }

  @Test
  @DisplayName("등록 시 제목이 255자를 초과하면 400 TITLE_TOO_LONG 에러를 반환한다")
  void createContent_titleTooLong() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.TEXT, "가".repeat(256), null, null, "본문");
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.TITLE_TOO_LONG));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("TITLE_TOO_LONG"))
        .andExpect(jsonPath("$.error.message").value("제목은 최대 255자까지 입력할 수 있습니다."));
  }

  @Test
  @DisplayName("URL 타입 등록 시 잘못된 형식의 URL이면 400 INVALID_URL 에러를 반환한다")
  void createContent_invalidUrlFormat() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "invalid-url-format", "본문", null);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.INVALID_URL));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_URL"))
        .andExpect(jsonPath("$.error.message").value("올바르지 않은 URL 형식입니다."));
  }

  @Test
  @DisplayName("URL 타입 등록 시 호스트가 없는 URL 형식이면 400 INVALID_URL 에러를 반환한다")
  void createContent_missingHostUrl() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    ContentCreateRequest request =
        new ContentCreateRequest(InputType.URL, "제목", "https:example.com", "본문", null);
    when(contentService.createContent(any(ContentCreateRequest.class), eq(memberUuid)))
        .thenThrow(new CustomException(ContentErrorCode.INVALID_URL));
    mockMvc
        .perform(
            post("/api/contents")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_URL"))
        .andExpect(jsonPath("$.error.message").value("올바르지 않은 URL 형식입니다."));
  }
}
