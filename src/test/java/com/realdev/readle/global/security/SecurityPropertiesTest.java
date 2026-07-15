package com.realdev.readle.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

  @Test
  void rejectsMissingOrShortSecurityKeys() {
    assertThatThrownBy(() -> properties("", validStateKey()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(null, validStateKey()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), "short"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedStateEncryptionKey() {
    assertThatThrownBy(() -> properties(validJwtSecret(), "not-base64!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsStateEncryptionKeysAtValidDecodedLengthBoundaries() {
    assertThat(properties(validJwtSecret(), "MDEyMzQ1Njc4OWFiY2RlZg==").stateEncryptionKeyBytes())
        .hasSize(16);
    assertThat(
            properties(validJwtSecret(), "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1u")
                .stateEncryptionKeyBytes())
        .hasSize(24);
    assertThat(properties(validJwtSecret(), validStateKey()).stateEncryptionKeyBytes()).hasSize(32);
  }

  @Test
  void rejectsTokenAndStateTtlsOutsideAllowedRanges() {
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 0, 14, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 61, 14, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 30, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 30, 31, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 30, 14, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(validJwtSecret(), validStateKey(), 30, 14, 11))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsNonDefaultTokenAndStateTtlsWithinAllowedRanges() {
    properties(validJwtSecret(), validStateKey(), 15, 7, 5);
    properties(validJwtSecret(), validStateKey(), 1, 14, 5);
    properties(validJwtSecret(), validStateKey(), 60, 14, 5);
    properties(validJwtSecret(), validStateKey(), 30, 1, 5);
    properties(validJwtSecret(), validStateKey(), 30, 30, 5);
    properties(validJwtSecret(), validStateKey(), 30, 14, 1);
    properties(validJwtSecret(), validStateKey(), 30, 14, 10);
  }

  private SecurityProperties properties(String jwtSecret, String stateEncryptionKey) {
    return properties(jwtSecret, stateEncryptionKey, 30, 14, 10);
  }

  private SecurityProperties properties(
      String jwtSecret,
      String stateEncryptionKey,
      int accessTokenMinutes,
      int refreshTokenDays,
      int stateMinutes) {
    return new SecurityProperties(
        "issuer",
        jwtSecret,
        "audience",
        accessTokenMinutes,
        refreshTokenDays,
        stateEncryptionKey,
        stateMinutes,
        "http://localhost:8080",
        List.of("/"),
        new SecurityProperties.OAuthProviders(
            new SecurityProperties.OAuthProviderSettings("", "", "", "", ""),
            new SecurityProperties.OAuthProviderSettings("", "", "", "", "")));
  }

  private String validJwtSecret() {
    return "01234567890123456789012345678901";
  }

  private String validStateKey() {
    return "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=";
  }
}
