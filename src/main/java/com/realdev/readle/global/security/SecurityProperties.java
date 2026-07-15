package com.realdev.readle.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
    String jwtIssuer,
    String jwtSecret,
    String jwtAudience,
    int accessTokenMinutes,
    int refreshTokenDays,
    String stateEncryptionKey,
    int stateMinutes,
    String backendOrigin,
    List<String> allowedReturnPaths,
    OAuthProviders oauth) {

  public SecurityProperties {
    requireAtLeast(jwtSecret, 32, "JWT secret");
    requireAesKey(stateEncryptionKey);
    requireInRange(accessTokenMinutes, 1, 60, "Access-token TTL");
    requireInRange(refreshTokenDays, 1, 30, "Refresh-token TTL");
    requireInRange(stateMinutes, 1, 10, "OAuth state TTL");
  }

  private static void requireAtLeast(String value, int minimumBytes, String name) {
    if (value == null || value.getBytes(StandardCharsets.UTF_8).length < minimumBytes) {
      throw new IllegalArgumentException(name + " must be at least " + minimumBytes + " bytes");
    }
  }

  private static void requireAesKey(String value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "OAuth state encryption key must be a 16, 24, or 32 byte AES key");
    }
    byte[] key;
    try {
      key = Base64.getDecoder().decode(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "OAuth state encryption key must be standard Base64", exception);
    }
    int length = key.length;
    if (length != 16 && length != 24 && length != 32) {
      throw new IllegalArgumentException(
          "OAuth state encryption key must be a 16, 24, or 32 byte AES key");
    }
  }

  public byte[] stateEncryptionKeyBytes() {
    return Base64.getDecoder().decode(stateEncryptionKey);
  }

  private static void requireInRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
  }

  public record OAuthProviders(OAuthProviderSettings google, OAuthProviderSettings kakao) {}

  public record OAuthProviderSettings(
      String clientId,
      String clientSecret,
      String authorizationUrl,
      String tokenUrl,
      String userInfoUrl) {}
}
