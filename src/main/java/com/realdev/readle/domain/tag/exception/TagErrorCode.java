package com.realdev.readle.domain.tag.exception;

import com.realdev.readle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TagErrorCode implements ErrorCode {
  INVALID_TAG_NAME(HttpStatus.BAD_REQUEST, "태그 이름은 비어있을 수 없습니다."),
  INVALID_TAG_COUNT(HttpStatus.BAD_REQUEST, "태그 개수는 1개 이상 3개 이하여야 합니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
