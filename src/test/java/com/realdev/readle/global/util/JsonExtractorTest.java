package com.realdev.readle.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonExtractorTest {

  @Test
  @DisplayName("순수 JSON 객체 텍스트는 그대로 유지된다")
  void extractJson_pureJsonObject() {
    String input = "{\"key\": \"value\", \"nested\": {\"a\": 1}}";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo(input);
  }

  @Test
  @DisplayName("순수 JSON 배열 텍스트는 그대로 유지된다")
  void extractJson_pureJsonArray() {
    String input = "[{\"id\": 1}, {\"id\": 2}]";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo(input);
  }

  @Test
  @DisplayName("마크다운 코드 블록(```json)이 포함된 경우 JSON 객체만 추출한다")
  void extractJson_markdownJsonBlock() {
    String input = "```json\n{\n  \"tags\": [\"Spring\"]\n}\n```";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\n  \"tags\": [\"Spring\"]\n}");
  }

  @Test
  @DisplayName("LLM 응답에 인사말/설명 문구가 앞뒤로 붙어도 JSON 영역만 정확히 추출한다")
  void extractJson_withSurroundingText() {
    String input =
        "안녕하세요! 요청하신 퀴즈 세트 결과입니다.\n"
            + "```json\n"
            + "{\n"
            + "  \"quizzes\": [{\"id\": 1}]\n"
            + "}\n"
            + "```\n"
            + "추가 질문이 있으시면 말씀해주세요.";

    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\n  \"quizzes\": [{\"id\": 1}]\n}");
  }

  @Test
  @DisplayName("주의 [참고] 등 텍스트 내 괄호가 앞서 등장해도 올바른 JSON 영역만 균형 있게 추출한다")
  void extractJson_surroundingBracketsInText() {
    String input = "주의 [참고] 결과입니다: {\"tags\": [\"Java\"]}";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\"tags\": [\"Java\"]}");
  }

  @Test
  @DisplayName("JSON 뒤에 불필요한 닫는 괄호 ] 가 덧붙어도 유효한 JSON 객체만 추출한다")
  void extractJson_trailingBracketAfterJson() {
    String input = "{\"id\": 1}]";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\"id\": 1}");
  }

  @Test
  @DisplayName("JSON 문자열 값 내부에 괄호가 포함되어 있어도 정상적으로 전체 JSON을 추출한다")
  void extractJson_bracketsInsideJsonStrings() {
    String input = "결과: {\"description\": \"[안내] Spring Boot 가이드\"}";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\"description\": \"[안내] Spring Boot 가이드\"}");
  }

  @Test
  @DisplayName("미완성된 마크다운 코드 펜스의 경우 파싱 실패 시 원문 텍스트를 유지한다")
  void extractJson_unterminatedMarkdownFence() {
    String input = "```json\n{\n  \"tags\": [\"Spring\"]";
    String result = JsonExtractor.extractJson(input);
    assertThat(result).isEqualTo("{\n  \"tags\": [\"Spring\"]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  @DisplayName("null 이거나 빈 문자열인 경우 빈 문자열을 반환한다")
  void extractJson_blankInput(String input) {
    assertThat(JsonExtractor.extractJson(input)).isEmpty();
    assertThat(JsonExtractor.extractJson(null)).isEmpty();
  }
}
