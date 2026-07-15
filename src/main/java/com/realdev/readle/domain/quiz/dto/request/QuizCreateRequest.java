package com.realdev.readle.domain.quiz.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuizCreateRequest {

  @NotNull(message = "검증 완료된 원본 콘텐츠 ID는 필수입니다.") private Long sourceValidationId;
}
