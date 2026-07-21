package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiValidationTxHelperTest {

  @Test
  @DisplayName("evidence_snippets가 3개를 초과하면 앞의 3개까지만 JSON 직렬화하여 반환한다")
  void serializeSnippets_exceeds3_truncatesTo3() {
    // given
    ObjectMapper objectMapper = new ObjectMapper();
    AiValidationTxHelper helper = new AiValidationTxHelper(null, null, objectMapper);

    List<String> snippets = List.of("증거1", "증거2", "증거3", "증거4", "증거5");

    // when
    // serializeSnippets는 private 메서드이므로 ReflectionTestUtils를 사용해 호출
    String json = ReflectionTestUtils.invokeMethod(helper, "serializeSnippets", 1L, snippets);

    // then
    assertThat(json).isNotNull();
    assertThat(json).contains("증거1", "증거2", "증거3");
    assertThat(json).doesNotContain("증거4", "증거5");
  }
}
