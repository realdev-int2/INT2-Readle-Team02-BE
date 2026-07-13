package com.realdev.readle.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.global.security.SecurityConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
    controllers = {
      ValidatedController.class,
      NonValidatedController.class,
      GlobalExceptionHandler.class
    })
@Import(SecurityConfig.class)
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("CustomException(4xx)이 발생하면 mapped status와 {code, message, timestamp}를 반환한다")
  void handleCustomException() throws Exception {
    mockMvc
        .perform(get("/test/custom-exception"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("CustomException에 커스텀 에러 메시지를 제공하면 해당 메시지를 반환한다")
  void handleCustomExceptionWithCustomMessage() throws Exception {
    mockMvc
        .perform(get("/test/custom-exception-message"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("ID가 5인 회원을 찾을 수 없습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("CustomException(5xx)이 발생하면 500 SERVER_ERROR를 반환한다")
  void handleCustomExceptionServerError() throws Exception {
    mockMvc
        .perform(get("/test/custom-server-error"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("예상치 못한 문제가 발생했습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("비즈니스/커스텀 예외 이외의 예상치 못한 예외가 발생하면 500 SERVER_ERROR를 반환한다")
  void handleUnexpectedException() throws Exception {
    mockMvc
        .perform(get("/test/unexpected-exception"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
        .andExpect(jsonPath("$.message").value("예상치 못한 문제가 발생했습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("RequestBody DTO 검증 실패 시 400 INVALID_INPUT과 상세 메시지를 반환한다")
  void handleMethodArgumentNotValid() throws Exception {
    TestRequest invalidRequest = new TestRequest("");

    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("name: 이름은 필수입니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("ConstraintViolationException 발생 시 위반 항목들의 메시지를 반환한다")
  void handleConstraintViolationException() throws Exception {
    mockMvc
        .perform(get("/test/validated/constraint-violation").param("name", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value(containsString("must not be blank")))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("ConstraintViolationException 발생 시 위반 정보(violations)가 없으면 기본 에러 메시지를 반환한다")
  void handleConstraintViolationExceptionWithNullViolations() throws Exception {
    mockMvc
        .perform(get("/test/manual/constraint-violation-null"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("컨트롤러 파라미터 검증 실패 시(HandlerMethodValidationException) 400 INVALID_INPUT을 반환한다")
  void handleHandlerMethodValidationException() throws Exception {
    mockMvc
        .perform(get("/test/non-validated/handler-method-validation").param("name", "   "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value(containsString("must not be blank")))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("지원하지 않는 HTTP Method로 요청하면 405 METHOD_NOT_SUPPORTED를 반환한다")
  void handleHttpRequestMethodNotSupported() throws Exception {
    mockMvc
        .perform(post("/test/custom-exception"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_SUPPORTED"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("읽을 수 없는 형식의 JSON Body 요청 시 400 INVALID_INPUT을 반환한다")
  void handleHttpMessageNotReadable() throws Exception {
    mockMvc
        .perform(
            post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("필수 쿼리 파라미터 누락 시 400 INVALID_INPUT을 반환한다")
  void handleMissingServletRequestParameter() throws Exception {
    mockMvc
        .perform(get("/test/non-validated/handler-method-validation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("유효한 입력 형식이 아닙니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("쿼리/패스 파라미터 타입 불일치 시 400 INVALID_INPUT과 커스텀 메시지를 반환한다")
  void handleTypeMismatch() throws Exception {
    mockMvc
        .perform(get("/test/type-mismatch").param("age", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.message").value("age 파라미터 타입이 올바르지 않습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("지원하지 않는 Content-Type으로 요청 시 415 UNSUPPORTED_MEDIA_TYPE을 반환한다")
  void handleHttpMediaTypeNotSupported() throws Exception {
    mockMvc
        .perform(
            post("/test/validation").contentType(MediaType.TEXT_PLAIN).content("some raw text"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
        .andExpect(jsonPath("$.message").value("지원하지 않는 Content-Type 입니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("지원하지 않는 Accept 타입으로 요청 시 406 NOT_ACCEPTABLE을 반환한다")
  void handleHttpMediaTypeNotAcceptable() throws Exception {
    mockMvc
        .perform(get("/test/html").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotAcceptable())
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
        .andExpect(jsonPath("$.message").value("요청한 응답 형식을 지원하지 않습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  @DisplayName("찾을 수 없는 경로 요청 시 404 NOT_FOUND를 반환한다")
  void handleNoResourceFoundException() throws Exception {
    mockMvc
        .perform(get("/test/non-existent-route"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
        .andExpect(jsonPath("$.timestamp").exists());
  }
}

@RestController
@Validated
class ValidatedController {

  @GetMapping("/test/validated/constraint-violation")
  public void triggerConstraintViolation(@RequestParam @NotBlank String name) {}
}

@RestController
class NonValidatedController {

  @GetMapping("/test/custom-exception")
  public void triggerCustomException() {
    throw new CustomException(GlobalErrorCode.NOT_FOUND);
  }

  @GetMapping("/test/custom-exception-message")
  public void triggerCustomExceptionWithMessage() {
    throw new CustomException(GlobalErrorCode.NOT_FOUND, "ID가 5인 회원을 찾을 수 없습니다.");
  }

  @GetMapping("/test/custom-server-error")
  public void triggerCustomServerError() {
    throw new CustomException(GlobalErrorCode.SERVER_ERROR);
  }

  @GetMapping("/test/unexpected-exception")
  public void triggerUnexpectedException() {
    throw new RuntimeException("Unexpected error");
  }

  @PostMapping("/test/validation")
  public void triggerValidation(@Valid @RequestBody TestRequest request) {}

  @GetMapping("/test/non-validated/handler-method-validation")
  public void triggerHandlerMethodValidation(@RequestParam @NotBlank String name) {}

  @GetMapping("/test/type-mismatch")
  public void triggerTypeMismatch(@RequestParam Integer age) {}

  @GetMapping(value = "/test/html", produces = MediaType.TEXT_HTML_VALUE)
  public String triggerHtml() {
    return "<html></html>";
  }

  @GetMapping("/test/manual/constraint-violation-null")
  public void triggerConstraintViolationNull() {
    throw new ConstraintViolationException("must not be blank", null);
  }
}

class TestRequest {
  @NotBlank(message = "이름은 필수입니다.") private String name;

  public TestRequest() {}

  public TestRequest(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
