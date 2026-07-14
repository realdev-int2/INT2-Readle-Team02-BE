package com.realdev.readle.global.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClaudeRequest {

  @JsonProperty("model")
  private String model;

  @JsonProperty("max_tokens")
  private int maxTokens;

  @JsonProperty("system")
  private String system;

  @JsonProperty("messages")
  private List<Message> messages;

  @Getter
  @Builder
  public static class Message {
    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;
  }
}
