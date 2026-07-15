package com.realdev.readle.domain.quiz.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QuizSubmitRequest {

  @Valid @NotNull private List<AnswerRequest> answers;

  @Getter
  @NoArgsConstructor
  public static class AnswerRequest {
    @NotNull private Long questionId;
    private Long submittedChoiceId;
    private String submittedAnswerText;
  }
}
