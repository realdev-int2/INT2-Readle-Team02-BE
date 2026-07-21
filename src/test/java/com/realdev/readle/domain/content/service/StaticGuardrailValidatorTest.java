package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    // 기본적으로 비속어 필터는 통과(false)한다고 가정
    when(badWordFiltering.check(anyString())).thenReturn(false);

    // 마스킹 되지 않은 원문 "초보자지원"이 그대로 들어오면 비속어로 오탐(true 리턴)하도록 설정
    when(badWordFiltering.check(argThat(s -> s != null && s.contains("초보자지원")))).thenReturn(true);

    // 정상적으로 마스킹(*****) 처리된 텍스트가 들어오면 통과(false 리턴)하도록 설정
    when(badWordFiltering.check(argThat(s -> s != null && s.contains("*****")))).thenReturn(false);

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
