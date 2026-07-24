package com.realdev.readle.domain.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realdev.readle.domain.content.dto.response.ClaudeValidationResponse;
import com.realdev.readle.domain.content.entity.*;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.domain.content.repository.ContentRepository;
import com.realdev.readle.domain.content.repository.ContentValidationRepository;
import com.realdev.readle.global.exception.CustomException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiValidationTxHelper {

  private final ContentRepository contentRepository;
  private final ContentValidationRepository contentValidationRepository;
  private final ObjectMapper objectMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long createPendingValidation(Long contentId) {
    Content content = contentRepository.getReferenceById(contentId);

    ContentValidation pending =
        ContentValidation.builder()
            .content(content)
            .validationMethod(ValidationMethod.AI)
            .status(ValidationStatus.PENDING)
            .build();

    return contentValidationRepository.save(pending).getId();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateValidationSuccess(Long validationId, ClaudeValidationResponse response) {
    ContentValidation validation = findValidationOrThrow(validationId);

    BigDecimal score = BigDecimal.valueOf(response.validationScore());
    ValidationStatus status = ValidationStatus.valueOf(response.status());

    if (status == ValidationStatus.REJECTED) {
      RejectReasonCode reasonCode = RejectReasonCode.valueOf(response.rejectReasonCode());
      String snippetsJson = serializeSnippets(validationId, response.evidenceSnippets());
      validation.markRejected(score, reasonCode, snippetsJson);
    } else {
      validation.markPassed(score);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateValidationFailed(Long validationId, ErrorCode errorCode) {
    ContentValidation validation = findValidationOrThrow(validationId);
    validation.markFailed(errorCode);
  }

  private ContentValidation findValidationOrThrow(Long validationId) {
    return contentValidationRepository
        .findById(validationId)
        .orElseThrow(
            () ->
                new CustomException(
                    ContentErrorCode.CONTENT_VALIDATION_NOT_FOUND,
                    "존재하지 않는 검증 대상입니다. ID: " + validationId));
  }

  private String serializeSnippets(Long validationId, List<String> snippets) {
    if (snippets == null || snippets.isEmpty()) {
      return null;
    }

    // 최대 3개까지만 저장하도록 강제 (AI가 지시사항을 무시하고 초과 반환하는 경우 대비)
    List<String> truncatedSnippets = snippets.size() > 3 ? snippets.subList(0, 3) : snippets;

    try {
      return objectMapper.writeValueAsString(truncatedSnippets);
    } catch (JsonProcessingException e) {
      // evidence_snippets는 DB 스키마상 유효한 JSON 배열이어야 하므로,
      // 직렬화 실패 시 잘못된 형식(toString())을 저장하지 않고 null로 남긴 뒤 로그로 추적
      log.error("[AI_VALIDATION] evidenceSnippets 직렬화 실패. validationId={}", validationId, e);
      return null;
    }
  }
}
