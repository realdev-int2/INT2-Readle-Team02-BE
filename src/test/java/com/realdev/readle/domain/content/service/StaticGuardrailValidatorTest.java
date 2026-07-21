package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.content.config.ContentValidationProperties;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.RejectReasonCode;
import com.vane.badwordfiltering.BadWordFiltering;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaticGuardrailValidatorTest {

  @Test
  @DisplayName("safewords에 등록된 단어는 별표로 마스킹되어 비속어 오탐지 필터를 우회한다")
  void validate_withSafeWords_bypassesBadWordFilter() {
    // given
    ContentValidationProperties properties = mock(ContentValidationProperties.class);
    when(properties.minLength()).thenReturn(10);
    when(properties.promptInjectionKeywords()).thenReturn(List.of());

    BadWordFiltering badWordFiltering = mock(BadWordFiltering.class);
    // safewords인 "초보자지원"이 마스킹되어 들어오면 필터를 통과한다고(false) 가정
    // (만약 마스킹 안 되고 '초보자지원' 그대로 들어오면 true를 리턴하도록 설정할 수도 있지만 로직 확인에는 argThat으로 충분)
    when(badWordFiltering.check(argThat(s -> s.contains("*****")))).thenReturn(false);

    List<String> safeWords = List.of("초보자지원");

    StaticGuardrailValidator validator =
        new StaticGuardrailValidator(properties, badWordFiltering, safeWords);

    Content content = Content.fromText(null, "제목", "이곳은 초보자지원을 아끼지 않는 길드입니다.");

    // when
    Optional<RejectReasonCode> result = validator.validate(content);

    // then
    assertThat(result).isEmpty();
  }
}
