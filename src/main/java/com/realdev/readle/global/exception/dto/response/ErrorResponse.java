package com.realdev.readle.global.exception.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.ErrorCode;
import java.util.List;
import org.springframework.http.HttpStatus;

public record ErrorResponse(@JsonIgnore HttpStatus status, Error error) {

  public static ErrorResponse error(CustomException exception) {
    ErrorCode errorCode = exception.getErrorCode();

    return of(errorCode.getStatus(), errorCode.getCode(), exception.getMessage());
  }

  public static ErrorResponse error(ErrorCode errorCode) {
    return of(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage());
  }

  public static ErrorResponse error(ErrorCode errorCode, String message) {
    return of(errorCode.getStatus(), errorCode.getCode(), message);
  }

  public static ErrorResponse of(HttpStatus status, String code, String message) {
    return new ErrorResponse(status, new Error(code, message, List.of()));
  }

  public record Error(String code, String message, List<Object> details) {}
}
