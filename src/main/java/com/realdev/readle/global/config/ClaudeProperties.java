package com.realdev.readle.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "anthropic.claude")
public class ClaudeProperties {

  private String apiKey;
  private String version;
  private String model;
  private String baseUrl;
}
