package com.realdev.readle.domain.content.exception;

import com.realdev.readle.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContentErrorCode implements ErrorCode {
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 콘텐츠를 찾을 수 없습니다."),
  INVALID_URL(HttpStatus.BAD_REQUEST, "올바르지 않은 URL 형식입니다."),
  CRAWLING_TIMEOUT(HttpStatus.UNPROCESSABLE_ENTITY, "웹 페이지 요청 시간이 초과되었습니다."),
  EXTRACT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "콘텐츠 본문 추출에 실패했습니다. 본문 텍스트를 직접 입력해 주세요."),
  CONTENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "텍스트가 15,000자를 초과합니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
