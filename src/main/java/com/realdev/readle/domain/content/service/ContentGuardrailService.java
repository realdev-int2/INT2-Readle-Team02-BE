package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.entity.*;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ContentGuardrailService {

  private final ContentRepository contentRepository;
  private final ContentValidationRepository contentValidationRepository;
  private final StaticGuardrailValidator staticGuardrailValidator;
  private final WhitelistValidator whitelistValidator;

  // 1, 2차 방어선만 짧게 트랜잭션으로 묶어 처리. AI 호출 전까지만 담당.
  @Transactional
  public GuardrailResult evaluate(Long contentId) {
    Content content =
        contentRepository
            .findById(contentId)
            .orElseThrow(() -> new CustomException(ContentErrorCode.CONTENT_NOT_FOUND));

    Optional<RejectReasonCode> rejectReasonCode = staticGuardrailValidator.validate(content);
    if (rejectReasonCode.isPresent()) {
      saveStaticGuardrailResult(content, rejectReasonCode.get());
      return GuardrailResult.done();
    }

    if (whitelistValidator.isEligibleForWhitelist(content)) {
      saveWhitelistResult(content);
      return GuardrailResult.done();
    }

    return GuardrailResult.needsAi(content);
  }

  private void saveStaticGuardrailResult(Content content, RejectReasonCode rejectReasonCode) {
    contentValidationRepository.save(
        createValidation(
            content,
            ValidationMethod.STATIC_GUARDRAIL,
            ValidationStatus.REJECTED,
            rejectReasonCode));
  }

  private void saveWhitelistResult(Content content) {
    contentValidationRepository.save(
        createValidation(content, ValidationMethod.WHITELIST, ValidationStatus.PASSED, null));
  }

  private ContentValidation createValidation(
      Content content,
      ValidationMethod method,
      ValidationStatus status,
      RejectReasonCode rejectReasonCode) {
    return ContentValidation.builder()
        .content(content)
        .validationMethod(method)
        .status(status)
        .rejectReasonCode(rejectReasonCode)
        .validatedAt(LocalDateTime.now())
        .build();
  }

  @Transactional
  public void markAsFailed(Long contentId, ErrorCode errorCode) {
    contentRepository
        .findById(contentId)
        .ifPresent(
            content -> {
              ContentValidation validation =
                  ContentValidation.builder()
                      .content(content)
                      .validationMethod(ValidationMethod.STATIC_GUARDRAIL)
                      .build();

              validation.markFailed(errorCode);

              contentValidationRepository.save(validation);
            });
  }

  public record GuardrailResult(boolean needsAiValidation, Content content) {
    static GuardrailResult done() {
      return new GuardrailResult(false, null);
    }

    static GuardrailResult needsAi(Content content) {
      return new GuardrailResult(true, content);
    }
  }
}
