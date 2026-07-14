package com.realdev.readle.global.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.config.ClaudeConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(ClaudeClient.class)
@Import(ClaudeConfig.class)
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

  @Test
  @DisplayName("Claude API 호출 시 올바른 요청 헤더와 모델 규격으로 HTTP 요청을 전송하고 응답을 파싱해야 한다")
  void generateMessageSuccess() throws Exception {
    // given
    String systemPrompt = "You are a quiz master";
    String userPrompt = "Generate quiz";

    Map<String, Object> mockResponseMap =
        Map.of(
            "id", "msg_12345",
            "type", "message",
            "role", "assistant",
            "content", List.of(Map.of("type", "text", "text", "Generated Quiz JSON text")),
            "model", "claude-sonnet-5",
            "usage", Map.of("input_tokens", 100, "output_tokens", 200));
    String responseJson = objectMapper.writeValueAsString(mockResponseMap);

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
}
