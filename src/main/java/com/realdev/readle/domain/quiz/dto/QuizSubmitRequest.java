package com.realdev.readle.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class QuizSubmitRequest {

    @Valid
    @NotNull
    private List<AnswerRequest> answers;

    @Getter
    @NoArgsConstructor
    public static class AnswerRequest {
        @NotNull
        private Long questionId;
        private Long submittedChoiceId;
        private String submittedAnswerText;
    }
}
