package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.entity.ErrorCode;
import com.realdev.readle.domain.content.entity.RejectReasonCode;

public class ValidationMessageConverter {
  private ValidationMessageConverter() {
    // Utility 클래스는 인스턴스화 방지
  }

  public static String convertRejectReasonMessage(RejectReasonCode code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case EMPTY_CONTENT -> "분석할 텍스트 내용을 찾을 수 없습니다. 내용이 정상적으로 입력되었는지 확인해 주세요.";
      case CONTENT_TOO_SHORT -> "분석하기에 텍스트 내용이 너무 짧습니다. 충분한 길이의 텍스트를 입력해 주세요.";
      case BAD_WORD -> "비속어 또는 서비스 정책상 부적절한 표현이 포함되어 있습니다. 내용을 수정한 후 다시 등록해 주세요.";
      case PROMPT_INJECTION_DETECTED ->
          "시스템 지시를 우회하거나 조작하려는 비정상적인 요청 패턴(프롬프트 인젝션)이 감지되었습니다. 올바른 콘텐츠를 입력해 주세요.";
      case NOT_DEVELOPMENT_RELATED -> "개발/기술 학습 콘텐츠로 인식되지 않았습니다. 관련된 콘텐츠를 등록해 주세요.";
      case LOW_CONFIDENCE -> "정확한 검증이 어렵습니다. 콘텐츠 내용을 다시 한번 확인해 주세요.";
    };
  }

  public static String convertErrorMessage(ErrorCode code) {
    if (code == null) {
      return null;
    }
    return switch (code) {
      case AI_SERVICE_ERROR -> "AI 검증 서비스에 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
      case TIMEOUT -> "AI 검증 요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
      case SCHEMA_INVALID -> "AI 검증 응답 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
      case UNKNOWN_ERROR -> "알 수 없는 시스템 오류가 발생했습니다. 관리자에게 문의해 주세요.";
    };
  }
}
