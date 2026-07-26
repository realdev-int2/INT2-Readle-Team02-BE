package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ErrorCode;
import com.realdev.readle.domain.content.entity.ValidationMethod;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentValidationServiceTest {

  @Mock private ContentGuardrailService contentGuardrailService;
  @Mock private AiValidationService aiValidationService;
  @Spy private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @InjectMocks private ContentValidationService contentValidationService;

  @Test
  void recordsWhitelistValidation() {
    given(contentGuardrailService.evaluate(1L))
        .willReturn(
            ContentGuardrailService.GuardrailResult.done(
                ValidationStatus.PASSED, ValidationMethod.WHITELIST));

    contentValidationService.validateContent(1L);

    assertThat(
            meterRegistry
                .get("readle.content.validation")
                .tags("status", "passed", "method", "whitelist")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsAiValidationResult() {
    Content content = org.mockito.Mockito.mock(Content.class);
    given(contentGuardrailService.evaluate(2L))
        .willReturn(ContentGuardrailService.GuardrailResult.needsAi(content));
    given(aiValidationService.runAiValidation(content)).willReturn(ValidationStatus.REJECTED);

    contentValidationService.validateContent(2L);

    assertThat(
            meterRegistry
                .get("readle.content.validation")
                .tags("status", "rejected", "method", "ai")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsStaticGuardrailFailure() {
    RuntimeException exception = new RuntimeException("guardrail failure");
    given(contentGuardrailService.evaluate(3L)).willThrow(exception);

    assertThatThrownBy(() -> contentValidationService.validateContent(3L)).isSameAs(exception);

    verify(contentGuardrailService)
        .markAsFailed(3L, ValidationMethod.STATIC_GUARDRAIL, ErrorCode.UNKNOWN_ERROR);
    assertThat(
            meterRegistry
                .get("readle.content.validation")
                .tags("status", "failed", "method", "static_guardrail")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void recordsAiValidationFailure() {
    Content content = org.mockito.Mockito.mock(Content.class);
    RuntimeException exception = new RuntimeException("AI failure");
    given(contentGuardrailService.evaluate(4L))
        .willReturn(ContentGuardrailService.GuardrailResult.needsAi(content));
    given(aiValidationService.runAiValidation(content)).willThrow(exception);

    assertThatThrownBy(() -> contentValidationService.validateContent(4L)).isSameAs(exception);

    verify(contentGuardrailService).markAsFailed(4L, ValidationMethod.AI, ErrorCode.UNKNOWN_ERROR);
    assertThat(
            meterRegistry
                .get("readle.content.validation")
                .tags("status", "failed", "method", "ai")
                .timer()
                .count())
        .isEqualTo(1);
  }
}
