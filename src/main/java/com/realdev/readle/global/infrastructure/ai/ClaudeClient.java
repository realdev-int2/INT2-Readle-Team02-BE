package com.realdev.readle.global.infrastructure.ai;

import com.realdev.readle.global.config.ClaudeProperties;
import com.realdev.readle.global.infrastructure.ai.dto.ClaudeRequest;
import com.realdev.readle.global.infrastructure.ai.dto.ClaudeResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ClaudeClient {

  private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

  private final RestClient claudeRestClient;
  private final ClaudeProperties properties;

  public ClaudeClient(RestClient claudeRestClient, ClaudeProperties properties) {
    this.claudeRestClient = claudeRestClient;
    this.properties = properties;
  }

  // 기본 모델(properties.getModel())을 사용하는 기본 버전
  public ClaudeResponse generateMessage(String systemPrompt, String userPrompt) {
    return generateMessage(properties.getModel(), systemPrompt, userPrompt);
  }

  // 외부 주입된 가변 모델명을 사용하며 일시적 에러(429/5xx/네트워크 끊김)에 대해서만 1회 재시도하는 오버로딩 버전
  public ClaudeResponse generateMessage(String model, String systemPrompt, String userPrompt) {
    try {
      return executeGenerateMessage(model, systemPrompt, userPrompt);
    } catch (RestClientResponseException e) {
      // 429 Too Many Requests 또는 5xx Server Error인 경우에만 1회 재시도
      if (isRetryable(e.getStatusCode())) {
        return retryCall(model, systemPrompt, userPrompt, e);
      }
      throw e;
    } catch (ResourceAccessException e) {
      // I/O 연결 장애 발생 시 1회 재시도
      return retryCall(model, systemPrompt, userPrompt, e);
    }
  }

  // HTTP 상태 코드에 따른 재시도 대상 판정
  private boolean isRetryable(HttpStatusCode statusCode) {
    return statusCode.is5xxServerError() || statusCode.value() == 429;
  }

  // 1회 재시도 전 500ms 백오프 후 재시도 수행
  private ClaudeResponse retryCall(
      String model, String systemPrompt, String userPrompt, RestClientException e) {
    log.warn(
        "[CLAUDE_API_WARNING] Claude API 일시적 호출 실패. 500ms 후 1회 재시도를 진행합니다. 에러: {}", e.getMessage());
    try {
      Thread.sleep(500);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
    try {
      return executeGenerateMessage(model, systemPrompt, userPrompt);
    } catch (RestClientException retryEx) {
      log.error(
          "[CLAUDE_API_ERROR] Claude API 재시도 호출도 실패하였습니다. 최종 실패 처리합니다. 에러: {}",
          retryEx.getMessage(),
          retryEx);
      throw retryEx;
    }
  }

  // 실제 HTTP POST 통신을 수행하는 내부 격리 메서드
  private ClaudeResponse executeGenerateMessage(
      String model, String systemPrompt, String userPrompt) {
    ClaudeRequest request =
        ClaudeRequest.builder()
            .model(model)
            .maxTokens(properties.getMaxTokens())
            .system(systemPrompt)
            .messages(
                List.of(ClaudeRequest.Message.builder().role("user").content(userPrompt).build()))
            .build();

    String rawResponse = claudeRestClient
        .post()
        .uri("/v1/messages")
        .body(request)
        .retrieve()
        .body(String.class);
    
    log.info("[CLAUDE_RAW_RESPONSE] {}", rawResponse);
    
    try {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawResponse, ClaudeResponse.class);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException("Claude 응답 파싱 실패", e);
    }
  }

  // 기본 모델(properties.getModel())을 사용하는 기본 버전
  public String getGeneratedText(String systemPrompt, String userPrompt) {
    return getGeneratedText(properties.getModel(), systemPrompt, userPrompt);
  }

  // 외부 주입된 가변 모델명을 사용하는 오버로딩 버전
  public String getGeneratedText(String model, String systemPrompt, String userPrompt) {
    ClaudeResponse response = generateMessage(model, systemPrompt, userPrompt);
    if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
      throw new IllegalStateException("Claude API로부터 비어있는 응답을 받았습니다.");
    }
    
    // "thinking" 블록 등이 섞여 올 수 있으므로 type == "text"인 블록을 찾습니다.
    String generatedText = response.getContent().stream()
        .filter(block -> "text".equals(block.getType()))
        .map(ClaudeResponse.Content::getText)
        .findFirst()
        .orElse(null);

    if (generatedText == null || generatedText.isBlank()) {
      throw new IllegalStateException("Claude API 응답에 유효한 텍스트 블록이 없습니다.");
    }
    return generatedText;
  }
}
