package com.realdev.readle.global.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ClaudeResponse {

  @JsonProperty("id")
  private String id;

  @JsonProperty("type")
  private String type;

  @JsonProperty("role")
  private String role;

  @JsonProperty("content")
  private List<Content> content;

  @JsonProperty("model")
  private String model;

  @JsonProperty("usage")
  private Usage usage;

  @Getter
  @NoArgsConstructor
  public static class Content {
    @JsonProperty("type")
    private String type;

    @JsonProperty("text")
    private String text;
  }

  @Getter
  @NoArgsConstructor
  public static class Usage {
    @JsonProperty("input_tokens")
    private int inputTokens;

    @JsonProperty("output_tokens")
    private int outputTokens;
  }
}
