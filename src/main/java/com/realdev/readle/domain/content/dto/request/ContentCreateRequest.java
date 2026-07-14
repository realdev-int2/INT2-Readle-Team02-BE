package com.realdev.readle.domain.content.dto.request;

import com.realdev.readle.domain.content.entity.InputType;
import jakarta.validation.constraints.NotNull;

public record ContentCreateRequest(
    @NotNull(message = "inputType은 필수 입력값입니다.") InputType inputType,
    String title,
    String url,
    String extractedText,
    String text) {}
