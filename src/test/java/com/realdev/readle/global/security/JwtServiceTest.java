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

  @Test
  void rejectsExpiredToken() {
    Clock laterClock = Clock.fixed(Instant.parse("2026-07-14T00:02:00Z"), ZoneOffset.UTC);
    JwtService issuingService = new JwtService(properties(properties.jwtSecret(), 1), clock);
    JwtService validatingService =
        new JwtService(properties(properties.jwtSecret(), 1), laterClock);

    String token = issuingService.issue("member-uuid");

    assertThatThrownBy(() -> validatingService.memberUuid(token))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void rejectsTamperedToken() {
    String token = jwtService.issue("member-uuid");
    int signatureStart = token.lastIndexOf('.') + 1;
    char replacement = token.charAt(signatureStart) == 'a' ? 'b' : 'a';
    String tamperedToken =
        token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

    assertThatThrownBy(() -> jwtService.memberUuid(tamperedToken))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void rejectsTokenWithDifferentIssuer() {
    JwtService anotherService =
        new JwtService(
            properties(properties.jwtSecret(), "another-issuer", properties.jwtAudience(), 30),
            clock);

    String token = anotherService.issue("member-uuid");

    assertThatThrownBy(() -> jwtService.memberUuid(token)).isInstanceOf(CustomException.class);
  }

  @Test
  void rejectsTokenWithDifferentAudience() {
    JwtService anotherService =
        new JwtService(
            properties(properties.jwtSecret(), properties.jwtIssuer(), "another-audience", 30),
            clock);

    String token = anotherService.issue("member-uuid");

    assertThatThrownBy(() -> jwtService.memberUuid(token)).isInstanceOf(CustomException.class);
  }

  private SecurityProperties properties(String jwtSecret) {
    return properties(jwtSecret, "readle-test-issuer", "readle-api", 30);
  }

  private SecurityProperties properties(String jwtSecret, int accessTokenMinutes) {
    return properties(jwtSecret, "readle-test-issuer", "readle-api", accessTokenMinutes);
  }

  private SecurityProperties properties(
      String jwtSecret, String jwtIssuer, String jwtAudience, int accessTokenMinutes) {
    return new SecurityProperties(
        jwtIssuer,
        jwtSecret,
        jwtAudience,
        accessTokenMinutes,
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
