package com.realdev.readle.domain.content.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "content.validation")
public record ContentValidationProperties(
    @Min(1) int minLength,
    @Min(1) int maxAttempts,
    @Min(1) long retryDelayMs,
    @Min(1) long callTimeoutSeconds,
    @NotEmpty List<String> promptInjectionKeywords,
    @NotBlank String badwordsKoResourcePath,
    @NotEmpty List<String> whitelistDomains) {}
