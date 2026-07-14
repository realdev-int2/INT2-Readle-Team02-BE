package com.realdev.readle.domain.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ClaudeQuizResponseDto {

    private List<String> tags;
    private List<ClaudeQuizDto> quizzes;

    @Getter
    @Setter
    @NoArgsConstructor
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
