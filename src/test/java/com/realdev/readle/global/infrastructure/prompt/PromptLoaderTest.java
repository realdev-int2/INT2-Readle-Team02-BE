package com.realdev.readle.global.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PromptLoaderTest {

  private final PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());

  @Test
  @DisplayName("프롬프트 파일 로딩 및 템플릿 변수 치환이 정상적으로 수행되어야 한다")
  void loadPromptSuccess() {
    // given
    String promptFile = "quiz-generation.txt";
    Map<String, String> variables =
        Map.of(
            "content", "Java Concurrency Article Content",
            "questionCount", "3");

    // when
    String result = promptLoader.loadPrompt(promptFile, variables);

    // then
    assertThat(result)
        .contains("Java Concurrency Article Content")
        .contains("Target Question Count: 3")
        .doesNotContain("${content}")
        .doesNotContain("${questionCount}");
  }

  @Test
  @DisplayName("존재하지 않는 프롬프트 파일 로딩 시 예외가 발생해야 한다")
  void loadPromptFileNotFound() {
    // given
    String invalidFile = "non-existent-prompt.txt";

    // when & then
    assertThatThrownBy(() -> promptLoader.loadPrompt(invalidFile, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("프롬프트 템플릿 파일을 찾을 수 없습니다");
  }
}
