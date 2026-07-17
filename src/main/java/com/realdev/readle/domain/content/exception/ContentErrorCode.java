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
  TITLE_REQUIRED(HttpStatus.BAD_REQUEST, "제목은 필수 입력값입니다."),
  TITLE_TOO_LONG(HttpStatus.BAD_REQUEST, "제목은 최대 255자까지 입력할 수 있습니다."),
  CONTENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "텍스트가 15,000자를 초과합니다."),
  URL_REQUIRED(HttpStatus.BAD_REQUEST, "URL 입력 시 url은 필수입니다."),
  TEXT_REQUIRED(HttpStatus.BAD_REQUEST, "텍스트 입력 시 text는 필수입니다."),
  MISSING_EXTRACTED_TEXT(
      HttpStatus.BAD_REQUEST, "URL 본문 추출 결과가 누락되었습니다. 먼저 본문 추출을 완료한 후 등록을 요청해 주세요."),
  UNNECESSARY_TEXT(HttpStatus.BAD_REQUEST, "URL 입력 시 text 필드는 비어 있어야 합니다."),
  UNNECESSARY_URL_INFO(HttpStatus.BAD_REQUEST, "텍스트 입력 시 url 및 extractedText 필드는 비어 있어야 합니다."),
  CONTENT_VALIDATION_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "검증 대상 콘텐츠의 검증 이력을 찾을 수 없습니다."),
  INVALID_AI_VALIDATION_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "AI 검증 응답 스키마가 유효하지 않습니다."),
  AI_VALIDATION_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "AI 검증 요청 시간이 초과되었습니다."),
  AI_VALIDATION_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 검증 서비스 연동 중 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
