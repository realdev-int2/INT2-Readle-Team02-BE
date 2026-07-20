package com.realdev.readle.domain.auth;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public final class OAuthStateCookie {

  public static final String NAME = "__Host-readle_oauth_state";

  private OAuthStateCookie() {}

  public static ResponseCookie create(String value, int stateMinutes) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/")
        .maxAge(Duration.ofMinutes(stateMinutes))
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
