package com.realdev.readle.global.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.config.ClaudeProperties;
import com.realdev.readle.global.config.ClaudeTestConfig;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.infrastructure.ai.dto.ClaudeResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@RestClientTest(ClaudeClient.class)
@Import(ClaudeTestConfig.class)
@EnableConfigurationProperties(ClaudeProperties.class)
@TestPropertySource(
    properties = {
      "anthropic.claude.api-key=mock-key",
      "anthropic.claude.base-url=https://api.anthropic.com",
      "anthropic.claude.model=claude-sonnet-5",
      "anthropic.claude.version=2023-06-01"
    })
class ClaudeClientTest {

  @Autowired private ClaudeClient claudeClient;

  @Autowired private MockRestServiceServer server;

  @Autowired private ObjectMapper objectMapper;

  // 반복되는 Claude 성공 응답 JSON을 생성하는 헬퍼 메서드
  private String mockResponseJson(String responseText) throws JsonProcessingException {
    Map<String, Object> responseMap =
        Map.of(
            "id", "msg_12345",
            "type", "message",
            "role", "assistant",
            "content", List.of(Map.of("type", "text", "text", responseText)),
            "model", "claude-sonnet-5",
            "usage", Map.of("input_tokens", 100, "output_tokens", 200));
    return objectMapper.writeValueAsString(responseMap);
  }

  @Test
  @DisplayName("Claude API 호출 시 올바른 요청 헤더와 모델 규격으로 HTTP 요청을 전송하고 응답을 파싱해야 한다")
  void generateMessageSuccess() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";
    String responseJson = mockResponseJson("Generated Quiz JSON text");

    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("x-api-key", "mock-key"))
        .andExpect(header("anthropic-version", "2023-06-01"))
        .andExpect(header("content-type", MediaType.APPLICATION_JSON_VALUE))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when
    String generatedText = claudeClient.getGeneratedText(systemPrompt, userPrompt);

    // then
    assertThat(generatedText).isEqualTo("Generated Quiz JSON text");
    server.verify();
  }

  @Test
  @DisplayName(
      "Claude API 응답의 content 목록이 비어있으면 getGeneratedText 호출 시 CustomException(SERVER_ERROR)을 던져야 한다")
  void getGeneratedTextThrowsExceptionOnEmptyResponse() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    Map<String, Object> mockResponseMap =
        Map.of(
            "id", "msg_12345",
            "type", "message",
            "role", "assistant",
            "content", Collections.emptyList(),
            "model", "claude-sonnet-5",
            "usage", Map.of("input_tokens", 100, "output_tokens", 200));
    String responseJson = objectMapper.writeValueAsString(mockResponseMap);

    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when & then
    assertThatThrownBy(() -> claudeClient.getGeneratedText(systemPrompt, userPrompt))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining("Claude API로부터 비어있는 응답을 받았습니다.");
    server.verify();
  }

  @Test
  @DisplayName(
      "Claude API 응답의 첫 번째 콘텐츠 블록의 text가 비어있으면 getGeneratedText 호출 시 CustomException(SERVER_ERROR)을 던져야 한다")
  void getGeneratedTextThrowsExceptionOnBlankTextBlock() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    Map<String, Object> mockResponseMap =
        Map.of(
            "id", "msg_12345",
            "type", "message",
            "role", "assistant",
            "content", List.of(Map.of("type", "text", "text", "")),
            "model", "claude-sonnet-5",
            "usage", Map.of("input_tokens", 100, "output_tokens", 200));
    String responseJson = objectMapper.writeValueAsString(mockResponseMap);

    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when & then
    assertThatThrownBy(() -> claudeClient.getGeneratedText(systemPrompt, userPrompt))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining("Claude API 응답에 유효한 텍스트 블록이 없습니다.");
    server.verify();
  }

  @Test
  @DisplayName("Claude API 최초 호출 시 5xx 에러가 발생하면 1회 재시도하여 성공적인 응답을 반환해야 한다")
  void generateMessageFailsAndRetriesOnApiException() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";
    String responseJson = mockResponseJson("Generated Quiz JSON text");

    // 1차 시도: 500 에러 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    // 2차 시도 (재시도): 200 성공 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when
    String response = claudeClient.getGeneratedText(systemPrompt, userPrompt);

    // then
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo("Generated Quiz JSON text");
    server.verify();
  }

  @Test
  @DisplayName("Claude API 최초 호출 시 I/O 연결 장애가 발생하면 1회 재시도하여 성공적인 응답을 반환해야 한다")
  void generateMessageFailsAndRetriesOnResourceAccessException() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";
    String responseJson = mockResponseJson("Generated Quiz JSON text");

    // 1차 시도: I/O 연결 장애 모킹 (ResourceAccessException 발생)
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "Connection refused", new IOException("Connection refused"));
            });

    // 2차 시도 (재시도): 200 성공 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when
    String response = claudeClient.getGeneratedText(systemPrompt, userPrompt);

    // then
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo("Generated Quiz JSON text");
    server.verify();
  }

  @Test
  @DisplayName("Claude API 호출 시 1차 및 재시도 모두 실패하면 최종적으로 RestClientException을 전파해야 한다")
  void generateMessageThrowsExceptionAfterRetryFailure() {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    // 1차 시도: 500 에러 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    // 2차 시도 (재시도): 500 에러 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    // when & then
    assertThatThrownBy(() -> claudeClient.getGeneratedText(systemPrompt, userPrompt))
        .isInstanceOf(RestClientException.class);
    server.verify();
  }

  @Test
  @DisplayName("Claude API 호출 시 4xx 클라이언트 예외(400, 401 등)가 발생하면 재시도를 하지 않고 즉시 예외를 전파해야 한다")
  void generateMessageDoesNotRetryOn4xxClientException() {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    // 1차 시도: 400 Bad Request 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    // when & then
    assertThatThrownBy(() -> claudeClient.getGeneratedText(systemPrompt, userPrompt))
        .isInstanceOf(HttpClientErrorException.class);
    server.verify();
  }

  @Test
  @DisplayName("Claude API 최초 호출 시 429 응답이 발생하면 1회 재시도하여 성공적인 응답을 반환해야 한다")
  void generateMessageRetriesOn429TooManyRequests() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";
    String responseJson = mockResponseJson("Generated Quiz JSON text");

    // 1차 시도: 429 Too Many Requests 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

    // 2차 시도 (재시도): 200 성공 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    // when
    String response = claudeClient.getGeneratedText(systemPrompt, userPrompt);

    // then
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo("Generated Quiz JSON text");
    server.verify();
  }

  @Test
  @DisplayName("Claude API 호출 시 I/O 연결 장애로 1차 및 재시도 모두 실패하면 최종적으로 예외를 전파해야 한다")
  void generateMessageThrowsExceptionAfterResourceAccessExhaustingRetry() {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    // 1차 시도: I/O 연결 장애 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "Connection refused", new IOException("Connection refused"));
            });

    // 2차 시도 (재시도): I/O 연결 장애 모킹
    server
        .expect(requestTo("https://api.anthropic.com/v1/messages"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            request -> {
              throw new ResourceAccessException(
                  "Connection refused", new IOException("Connection refused"));
            });

    // when & then
    assertThatThrownBy(() -> claudeClient.getGeneratedText(systemPrompt, userPrompt))
        .isInstanceOf(ResourceAccessException.class);
    server.verify();
  }
}
