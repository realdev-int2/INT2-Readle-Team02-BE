package com.realdev.readle.domain.content.dto.response;

import com.realdev.readle.domain.content.entity.ValidationStatus;

public record ContentCreateResponse(Long contentId, ValidationStatus validationStatus) {}
