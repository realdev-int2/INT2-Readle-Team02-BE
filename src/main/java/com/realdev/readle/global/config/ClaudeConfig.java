package com.realdev.readle.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClaudeProperties.class)
public class ClaudeConfig {

  @Bean
  public RestClient claudeRestClient(
      RestClient.Builder restClientBuilder, ClaudeProperties properties) {
    return restClientBuilder
        .baseUrl(properties.getBaseUrl())
        .defaultHeader("x-api-key", properties.getApiKey())
        .defaultHeader("anthropic-version", properties.getVersion())
        .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
