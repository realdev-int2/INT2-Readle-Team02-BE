package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.config.ContentValidationProperties;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.RejectReasonCode;
import com.vane.badwordfiltering.BadWordFiltering;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class StaticGuardrailValidator {

  private final ContentValidationProperties properties;
  private final BadWordFiltering badWordFiltering;

  private final List<String> safeWords;

  public StaticGuardrailValidator(
      ContentValidationProperties properties,
      BadWordFiltering badWordFiltering,
      @Qualifier("safeWords") List<String> safeWords) {
    this.properties = properties;
    this.badWordFiltering = badWordFiltering;
    this.safeWords = safeWords;
  }

  public Optional<RejectReasonCode> validate(Content content) {
    String text =
        content.getExtractedText() != null ? content.getExtractedText() : content.getRawText();

    if (text == null || text.isBlank()) {
      return Optional.of(RejectReasonCode.EMPTY_CONTENT);
    }

    if (text.length() < properties.minLength()) {
      return Optional.of(RejectReasonCode.CONTENT_TOO_SHORT);
    }

    String safeText = text;
    if (safeWords != null) {
      for (String safeWord : safeWords) {
        safeText = safeText.replace(safeWord, "*".repeat(safeWord.length()));
      }
    }

    if (badWordFiltering.check(safeText)) {
      return Optional.of(RejectReasonCode.BAD_WORD);
    }

    String lowerText = text.toLowerCase();
    for (String keyword : properties.promptInjectionKeywords()) {
      if (lowerText.contains(keyword.toLowerCase())) {
        return Optional.of(RejectReasonCode.PROMPT_INJECTION_DETECTED);
      }
    }

    return Optional.empty();
  }
}
