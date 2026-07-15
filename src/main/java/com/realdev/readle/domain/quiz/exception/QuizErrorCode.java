package com.realdev.readle.domain.quiz.exception;

import com.realdev.readle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuizErrorCode implements ErrorCode {
  QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 퀴즈 세트입니다."),
  ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 풀이 시도입니다."),
  VALIDATION_NOT_PASSED(HttpStatus.BAD_REQUEST, "해당 콘텐츠는 퀴즈 생성이 불가능한 상태입니다."),
  QUIZ_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "퀴즈 생성 중 오류가 발생했습니다."),
  QUIZ_GRADING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 채점 처리 중 오류가 발생했습니다."),
  ALREADY_SUBMITTED(HttpStatus.BAD_REQUEST, "이미 제출이 완료되었거나 진행 중이 아닌 풀이입니다."),
  INVALID_ANSWER_COUNT(HttpStatus.BAD_REQUEST, "제출된 답안의 개수가 퀴즈의 전체 문제 수와 일치하지 않습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
