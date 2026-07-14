package com.realdev.readle.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ContentExtractRequest(@NotBlank(message = "URL은 필수 입력값입니다.") String url) {}
