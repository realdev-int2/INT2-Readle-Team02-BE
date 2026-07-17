package com.realdev.readle.domain.content.service;

import java.util.UUID;

public record ContentCreatedEvent(Long contentId, UUID memberUuid) {}
