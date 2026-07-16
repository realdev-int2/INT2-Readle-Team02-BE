package com.realdev.readle.domain.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class RefreshTokenCookie {

  public static final String NAME = "__Host-readle_refresh_token";

  private RefreshTokenCookie() {}

  public static ResponseCookie create(String value, int refreshTokenDays) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofDays(refreshTokenDays))
        .build();
  }

  public static ResponseCookie delete() {
    return ResponseCookie.from(NAME, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }
}
