package com.realdev.readle.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClaudeQuizResponseDto {

  private List<String> tags;
  private List<ClaudeQuizDto> quizzes;

  @Getter
  @Setter
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ClaudeQuizDto {
    private Integer id;
    private String type;
    private String question;
    private List<String> options;

    @JsonProperty("code_snippet")
    private String codeSnippet;

    private String answer;
  }
}
