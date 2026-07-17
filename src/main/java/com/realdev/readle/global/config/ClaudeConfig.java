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

    // 실제 로컬/프로덕션 런타임 환경에 맞춘 Claude 전용 RequestFactory 및 타임아웃 격리 빌드
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(30));

    return restClientBuilder
        .clone()
        .requestFactory(requestFactory)
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @Bean
  public RestClient gradingClaudeRestClient(
      RestClient.Builder restClientBuilder, ClaudeProperties properties) {

    // 퀴즈 채점 전용: 스레드 고갈 방지를 위해 타이트한 타임아웃(3초) 적용
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(3));

    return restClientBuilder
        .clone()
        .requestFactory(requestFactory)
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @Bean
  public RestClient validationClaudeRestClient(
      RestClient.Builder restClientBuilder, ClaudeProperties properties) {

    // 콘텐츠 검증 전용: 애플리케이션 레벨 타임아웃(5초, ContentValidationProperties.callTimeoutSeconds)보다
    // HTTP readTimeout을 짧게 잡아, HTTP 클라이언트가 먼저 실패하도록 하여
    // CompletableFuture.get() 타임아웃 시 스레드가 계속 점유되는 상황을 방지한다.
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(Duration.ofSeconds(4));

    return restClientBuilder
        .clone()
        .requestFactory(requestFactory)
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
