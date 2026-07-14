package com.realdev.readle.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.exception.ErrorCode;
import com.realdev.readle.global.exception.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

public class SecurityErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        ErrorResponse.of(errorCode.getStatus(), errorCode.getCode(), errorCode.getMessage()));
  }
}
