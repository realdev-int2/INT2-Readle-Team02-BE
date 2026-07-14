package com.realdev.readle.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.service.ContentService;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ContentController.class)
@Import(SecurityConfig.class)
class ContentControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ContentService contentService;

  @Test
  @DisplayName("정상적인 URL 요청 시 본문과 제목을 추출하여 200 OK를 반환한다")
  void extractSuccess() throws Exception {
    // given
    ContentExtractRequest request = new ContentExtractRequest("https://example.com");
    ContentExtractResponse response = new ContentExtractResponse("추출된 제목", "추출된 마크다운 본문");
    when(contentService.extract(any(ContentExtractRequest.class))).thenReturn(response);

    // when & then
    mockMvc
        .perform(
            post("/api/contents/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("추출된 제목"))
        .andExpect(jsonPath("$.content").value("추출된 마크다운 본문"));
  }

  @Test
  @DisplayName("요청 DTO의 URL이 빈 값일 경우 400 INVALID_INPUT 에러를 반환한다")
  void extractValidationFailure() throws Exception {
    // given
    ContentExtractRequest request = new ContentExtractRequest("   ");

    // when & then
    mockMvc
        .perform(
            post("/api/contents/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("url: URL은 필수 입력값입니다."));
  }

  @Test
  @DisplayName("잘못된 스킴의 URL이 전송되면 400 INVALID_URL 에러를 반환한다")
  void extractInvalidUrl() throws Exception {
    // given
    ContentExtractRequest request = new ContentExtractRequest("invalid-url-scheme");
    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.INVALID_URL));

    // when & then
    mockMvc
        .perform(
            post("/api/contents/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"))
        .andExpect(jsonPath("$.message").value("올바르지 않은 URL 형식입니다."));
  }

  @Test
  @DisplayName("크롤링 타임아웃 발생 시 422 CRAWLING_TIMEOUT 에러를 반환한다")
  void extractTimeout() throws Exception {
    // given
    ContentExtractRequest request = new ContentExtractRequest("https://timeout-url.com");
    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.CRAWLING_TIMEOUT));

    // when & then
    mockMvc
        .perform(
            post("/api/contents/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CRAWLING_TIMEOUT"))
        .andExpect(jsonPath("$.message").value("웹 페이지 요청 시간이 초과되었습니다."));
  }

  @Test
  @DisplayName("크롤링 연결 실패 또는 본문 정제 실패 시 422 EXTRACT_FAILED 에러를 반환한다")
  void extractFailed() throws Exception {
    // given
    ContentExtractRequest request = new ContentExtractRequest("https://blocked-url.com");
    when(contentService.extract(any(ContentExtractRequest.class)))
        .thenThrow(new CustomException(ContentErrorCode.EXTRACT_FAILED));

    // when & then
    mockMvc
        .perform(
            post("/api/contents/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("EXTRACT_FAILED"))
        .andExpect(jsonPath("$.message").value("콘텐츠 본문 추출에 실패했습니다. 본문 텍스트를 직접 입력해 주세요."));
  }
}
