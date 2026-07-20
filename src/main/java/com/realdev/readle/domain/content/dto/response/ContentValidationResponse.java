package com.realdev.readle.domain.content.dto.response;

import com.realdev.readle.domain.content.entity.ValidationStatus;
import java.time.LocalDateTime;

public record ContentValidationResponse(
    Long contentId,
    ValidationStatus status,
    String errorCode,
    String message,
    boolean bypassAvailable,
    LocalDateTime requestedAt,
    LocalDateTime validatedAt) {}
