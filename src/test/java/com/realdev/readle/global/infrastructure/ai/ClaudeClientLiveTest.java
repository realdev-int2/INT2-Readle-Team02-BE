package com.realdev.readle.global.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.realdev.readle.global.infrastructure.ai.dto.ClaudeRequest;
import com.realdev.readle.global.infrastructure.ai.dto.ClaudeResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Tag("live")
@DisplayName("Claude API 실제 통신 라이브 테스트")
class ClaudeClientLiveTest {

  private static RestClient rawRestClient;
  private static String apiKey;

  @BeforeAll
  static void setUpEnv() {
    apiKey = "";
    String baseUrl = "https://api.anthropic.com";

    Path envPath = Paths.get(".env");
    if (Files.exists(envPath)) {
      try {
        for (String line : Files.readAllLines(envPath)) {
          line = line.trim();
          if (line.startsWith("#") || !line.contains("=")) {
            continue;
          }
          int eqIdx = line.indexOf('=');
          String key = line.substring(0, eqIdx).trim();
          String value = line.substring(eqIdx + 1).trim();

          // 큰따옴표 또는 작은따옴표 제거 처리
          if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
          } else if (value.startsWith("'") && value.endsWith("'")) {
            value = value.substring(1, value.length() - 1);
          }

          value = value.replaceAll("[\\r\\n\\t\\s]", "");

          if ("CLAUDE_API_KEY".equals(key)) {
            apiKey = value;
          }
        }
      } catch (IOException e) {
        System.err.println("실제 통신 테스트용 .env 파일을 로딩하는 데 실패했습니다: " + e.getMessage());
      }
    }

    if (System.getenv("CLAUDE_API_KEY") != null) {
      apiKey = System.getenv("CLAUDE_API_KEY").replaceAll("[\\r\\n\\t\\s]", "");
    }

    System.err.println("==================================================");
    System.err.println("[DEBUG] Loaded API Key Length: " + (apiKey != null ? apiKey.length() : 0));
    System.err.println(
        "[DEBUG] Loaded API Key Prefix: "
            + (apiKey != null && apiKey.length() > 15 ? apiKey.substring(0, 15) : "none"));
    System.err.println("==================================================");

    rawRestClient =
        RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory())
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Test
  @DisplayName("실제 클로드 API 호출을 보내고 올바른 응답을 받아오는지 확인한다")
  void sendLiveMessageToClaude() {
    // Given
    String systemPrompt = "당신은 친절한 AI 어시스턴트입니다. 모든 답변은 한국어로 반말로 대답하고, 한 문장으로 제한하십시오.";
    String userPrompt = "안녕? 자기소개 한 문장 해줘.";

    // 키가 허용하는 가용 모델 목록 중 가장 높은 사양인 claude-sonnet-5를 지정
    ClaudeRequest request =
        ClaudeRequest.builder()
            .model("claude-sonnet-5")
            .maxTokens(100)
            .system(systemPrompt)
            .messages(
                List.of(ClaudeRequest.Message.builder().role("user").content(userPrompt).build()))
            .build();

    // When - 절대 경로 전체를 직접 명시하여 포스팅
    ClaudeResponse response =
        rawRestClient
            .post()
            .uri("https://api.anthropic.com/v1/messages")
            .body(request)
            .retrieve()
            .body(ClaudeResponse.class);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();

    String contentText = response.getContent().get(0).getText();
    System.err.println("==================================================");
    System.err.println("[CLAUDE LIVE RESPONSE]: " + contentText);
    System.err.println("==================================================");

    assertThat(contentText).isNotBlank();
  }
}
