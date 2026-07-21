package com.realdev.readle.domain.content.dto.response;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realdev.readle.domain.content.entity.RejectReasonCode;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.global.exception.CustomException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClaudeValidationResponseTest {

  @Test
  @DisplayName("60점 이상인데 REJECTED 상태이면 CustomException이 발생한다")
  void new_score60AndRejected_throwsException() {
    assertThatThrownBy(
            () -> {
              ClaudeValidationResponse response =
                  new ClaudeValidationResponse(
                      60,
                      ValidationStatus.REJECTED.name(),
                      RejectReasonCode.NOT_DEVELOPMENT_RELATED.name(),
                      List.of("증거"));
              response.validateSchema();
            })
        .isInstanceOf(CustomException.class)
        .hasMessageContaining("validationScore가 60점 이상이면 status는 반드시 PASSED여야 합니다");
  }

  @Test
  @DisplayName("60점 미만인데 PASSED 상태이면 CustomException이 발생한다")
  void new_score59AndPassed_throwsException() {
    assertThatThrownBy(
            () -> {
              ClaudeValidationResponse response =
                  new ClaudeValidationResponse(
                      59, ValidationStatus.PASSED.name(), null, List.of("증거"));
              response.validateSchema();
            })
        .isInstanceOf(CustomException.class)
        .hasMessageContaining("validationScore가 60점 미만이면 status는 반드시 REJECTED여야 합니다");
  }

  @Test
  @DisplayName("정상적인 모순 없는 값은 객체가 정상적으로 생성된다")
  void new_validInputs_createsSuccessfully() {
    assertThatCode(
            () -> {
              ClaudeValidationResponse response =
                  new ClaudeValidationResponse(
                      70, ValidationStatus.PASSED.name(), null, List.of("증거"));
              response.validateSchema();
            })
        .doesNotThrowAnyException();
  }
}
