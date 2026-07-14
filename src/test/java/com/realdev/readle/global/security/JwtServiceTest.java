package com.realdev.readle.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realdev.readle.global.exception.CustomException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final SecurityProperties properties = properties("01234567890123456789012345678901");
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
  private final JwtService jwtService = new JwtService(properties, clock);

  @Test
  void issuesAndValidatesHs256AccessTokenForMemberUuid() {
    String token = jwtService.issue("member-uuid");

    assertThat(jwtService.memberUuid(token)).isEqualTo("member-uuid");
  }

  @Test
  void rejectsTokenSignedWithAnotherKey() {
    String token = jwtService.issue("member-uuid");
    JwtService anotherService =
        new JwtService(properties("abcdefghijklmnopqrstuvwxyz123456"), clock);

    assertThatThrownBy(() -> anotherService.memberUuid(token)).isInstanceOf(CustomException.class);
  }

  private SecurityProperties properties(String jwtSecret) {
    return new SecurityProperties(
        "readle-test-issuer",
        jwtSecret,
        "readle-api",
        30,
        14,
        "abcdefghijklmnopqrstuvwxyz123456",
        10,
        "http://localhost:8080",
        List.of("/"),
        new SecurityProperties.OAuthProviders(
            new SecurityProperties.OAuthProviderSettings("", "", "", "", ""),
            new SecurityProperties.OAuthProviderSettings("", "", "", "", "")));
  }
}
