package com.realdev.readle.global.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

  @Bean
  public RestClient claudeRestClient(
      RestClient.Builder restClientBuilder, ClaudeProperties properties) {

    // 1. 공통 헤더 및 Base URL 설정
    restClientBuilder
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE);

    // 단위 테스트(MockRestServiceServer) 환경 확인
    boolean isMockEnv = properties.getApiKey() != null && properties.getApiKey().startsWith("mock");

    // 단위 테스트 환경이면 Mocking 가로채기 팩토리 바인딩 보존을 위해 원본 빌더를 복제(clone)하지 않고 그대로 빌드하여 리턴
    if (isMockEnv) {
      return restClientBuilder.build();
    }

    // 실제 로컬/프로덕션 런타임 환경에서만 clone()을 획득하고 Claude 전용 RequestFactory/타임아웃을 입혀 격리 빌드
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(30));

    return restClientBuilder.clone().requestFactory(requestFactory).build();
  }
}
