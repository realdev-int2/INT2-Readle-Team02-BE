package com.realdev.readle.domain.content.dto.response;

import com.realdev.readle.domain.content.entity.RejectReasonCode;
import com.realdev.readle.domain.content.entity.ValidationStatus;
import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.global.exception.CustomException;
import java.util.List;

public record ClaudeValidationResponse(
    Integer validationScore,
    String status,
    String rejectReasonCode,
    List<String> evidenceSnippets) {
  public void validateSchema() {
    if (validationScore == null || validationScore < 0 || validationScore > 100) {
      throw new CustomException(
          ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE,
          "validationScore는 0에서 100 사이의 필수 정수입니다. 값: " + validationScore);
    }

    ValidationStatus validationStatus;
    try {
      validationStatus = ValidationStatus.valueOf(status);
    } catch (Exception e) {
      throw new CustomException(
          ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE, "유효하지 않은 status 상태값입니다. 값: " + status);
    }

    // AI 검증 응답은 PASSED/REJECTED만 허용. PENDING/FAILED는 우리 시스템이 관리하는 상태이지
    // AI가 직접 반환해야 할 값이 아님
    if (validationStatus != ValidationStatus.PASSED
        && validationStatus != ValidationStatus.REJECTED) {
      throw new CustomException(
          ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE,
          "AI 응답의 status는 PASSED 또는 REJECTED만 허용됩니다. 값: " + status);
    }

    if (validationStatus == ValidationStatus.REJECTED) {
      if (rejectReasonCode == null || rejectReasonCode.isBlank()) {
        throw new CustomException(
            ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE,
            "REJECTED 상태 시 rejectReasonCode는 필수입니다.");
      }
      try {
        RejectReasonCode.valueOf(rejectReasonCode);
      } catch (Exception e) {
        throw new CustomException(
            ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE,
            "유효하지 않은 rejectReasonCode입니다. 값: " + rejectReasonCode);
      }
      if (evidenceSnippets == null
          || evidenceSnippets.isEmpty()
          || evidenceSnippets.stream().allMatch(String::isBlank)) {
        throw new CustomException(
            ContentErrorCode.INVALID_AI_VALIDATION_RESPONSE,
            "REJECTED 상태 시 evidenceSnippets는 필수입니다.");
      }
    }
  }
}
