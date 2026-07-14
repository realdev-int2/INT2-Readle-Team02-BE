package com.realdev.readle.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "anthropic.claude")
public class ClaudeProperties {

  @NotBlank(message = "Claude API Key는 필수 설정 값입니다.") private String apiKey;

  @NotBlank(message = "Claude API 버전은 필수 설정 값입니다.") private String version;

  @NotBlank(message = "Claude 디폴트 모델명은 필수 설정 값입니다.") private String model;

  @NotBlank(message = "Claude API Base URL은 필수 설정 값입니다.") private String baseUrl;

  /** Claude API 응답 최대 토큰 수 (기본값: 4000) */
  @Positive(message = "최대 토큰 수는 1 이상의 양수여야 합니다.") private int maxTokens = 4000;
}
