package com.realdev.readle.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class ClaudeTestConfig {

  @Bean
  @Primary
  public RestClient claudeTestRestClient(
      RestClient.Builder restClientBuilder, ClaudeProperties properties) {

    // 단위 테스트(MockRestServiceServer) 환경에서는 Mocking 가로채기 팩토리 바인딩 보존을 위해
    // JdkClientHttpRequestFactory 등의 실제 통신 팩토리를 덮어씌우지 않고 원본 빌더를 복제하여 사용합니다.
    return restClientBuilder
        .clone()
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
