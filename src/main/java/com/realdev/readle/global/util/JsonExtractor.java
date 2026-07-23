package com.realdev.readle.global.util;

/** Claude AI 등의 LLM 응답 텍스트에서 유효한 JSON (객체 또는 배열) 부분만 안전하게 추출하는 유틸리티 클래스. */
public class JsonExtractor {

  private JsonExtractor() {
    // 유틸리티 클래스 인스턴스화 방지
  }

  /**
   * 입력 텍스트에서 마크다운 블록(```json ... ```) 또는 가장 외곽의 JSON ({...} / [...]) 영역을 추출합니다.
   *
   * @param text LLM 원본 응답 텍스트
   * @return 추출된 정제된 JSON 문자열 (입력이 null이거나 빈 경우 빈 문자열 반환)
   */
  public static String extractJson(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }

    String trimmed = text.trim();

    // 1. 마크다운 코드 블록(```json ... ``` 또는 ``` ... ```) 제거 시도
    if (trimmed.startsWith("```")) {
      int firstNewLine = trimmed.indexOf('\n');
      int lastBackticks = trimmed.lastIndexOf("```");

      if (firstNewLine != -1 && lastBackticks > firstNewLine) {
        trimmed = trimmed.substring(firstNewLine + 1, lastBackticks).trim();
      } else {
        // 단일 줄 ```json {"a":1} ``` 형태 등 예외적 마크다운 대처
        trimmed = trimmed.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
      }
    }

    // 2. 가장 첫번째 '{' 또는 '[' 문자의 위치와 가장 마지막 '}' 또는 ']' 문자의 위치 추출
    int firstObject = trimmed.indexOf('{');
    int firstArray = trimmed.indexOf('[');

    int startPos = -1;
    if (firstObject != -1 && firstArray != -1) {
      startPos = Math.min(firstObject, firstArray);
    } else if (firstObject != -1) {
      startPos = firstObject;
    } else if (firstArray != -1) {
      startPos = firstArray;
    }

    int lastObject = trimmed.lastIndexOf('}');
    int lastArray = trimmed.lastIndexOf(']');
    int endPos = Math.max(lastObject, lastArray);

    if (startPos != -1 && endPos != -1 && startPos < endPos) {
      return trimmed.substring(startPos, endPos + 1).trim();
    }

    return trimmed;
  }
}
