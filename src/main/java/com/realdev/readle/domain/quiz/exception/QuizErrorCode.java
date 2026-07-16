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
  ATTEMPT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출이 완료되었거나 진행 중이 아닌 풀이입니다."),
  QUIZ_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 생성이 완료되지 않은 퀴즈 세트입니다."),
  INVALID_ANSWER_COUNT(HttpStatus.BAD_REQUEST, "제출된 답안의 개수가 퀴즈의 전체 문제 수와 일치하지 않습니다."),
  EMPTY_ARTICLE_TEXT(HttpStatus.BAD_REQUEST, "본문 텍스트가 비어있어 AI 채점을 수행할 수 없습니다."),
  INVALID_ANSWER_FORMAT(HttpStatus.BAD_REQUEST, "제출된 답안의 형식이 올바르지 않거나 허용되지 않는 패턴이 포함되어 있습니다."),
  FORBIDDEN_ACCESS(HttpStatus.FORBIDDEN, "해당 퀴즈 풀이 내역에 접근할 권한이 없습니다."),
  ATTEMPT_NOT_SUBMITTED(HttpStatus.BAD_REQUEST, "아직 채점이 완료되지 않은 풀이 시도입니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
