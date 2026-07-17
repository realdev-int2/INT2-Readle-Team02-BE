package com.realdev.readle.domain.quiz.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;

public enum QuestionType {
  MULTIPLE_CHOICE("multiple_choice"),
  SHORT_ANSWER("short_answer"),
  CODE_BLANK("code_blank");

  private final String value;

  QuestionType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static QuestionType fromValue(String value) {
    for (QuestionType type : QuestionType.values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new CustomException(GlobalErrorCode.INVALID_INPUT, "알 수 없는 문제 타입입니다: " + value);
  }
}
