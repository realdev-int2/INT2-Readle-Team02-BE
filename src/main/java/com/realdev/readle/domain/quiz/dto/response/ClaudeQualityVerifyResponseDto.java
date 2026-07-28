package com.realdev.readle.domain.quiz.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeQualityVerifyResponseDto(List<QuestionVerifyResult> results) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record QuestionVerifyResult(int index, boolean hasLeak, String reason) {}
}
