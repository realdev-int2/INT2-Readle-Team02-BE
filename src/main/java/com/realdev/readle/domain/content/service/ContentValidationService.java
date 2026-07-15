package com.realdev.readle.domain.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentValidationService {

  private final ContentGuardrailService contentGuardrailService;
  private final AiValidationService aiValidationService;

  // 트랜잭션을 열지 않음. 1,2차 방어선(짧은 트랜잭션)이 완전히 커밋된 뒤에만
  // AI 호출(트랜잭션 없이 진행)로 넘어가도록 하여, AI 호출 동안 DB 커넥션을 점유하지 않는다.
  public void validateContent(Long contentId) {
    ContentGuardrailService.GuardrailResult result = contentGuardrailService.evaluate(contentId);

    if (result.needsAiValidation()) {
      log.info("[VALIDATION] 1,2차 통과. 3차 AI 검증으로 위임. Content ID: {}", contentId);
      aiValidationService.runAiValidation(result.content());
    }
  }
}
