package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.MemberRefreshToken;
import com.realdev.readle.domain.member.repository.MemberRefreshTokenRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private MemberRefreshTokenRepository refreshTokenRepository;

  @Test
  void revokesOnlyPresentedRefreshToken() throws Exception {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    Member member = mock(Member.class);
    when(member.getUuid()).thenReturn("member-uuid");
    when(refreshTokenRepository.save(any(MemberRefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String rawToken = service.issue(member);
    String expectedHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    ArgumentCaptor<MemberRefreshToken> saved = ArgumentCaptor.forClass(MemberRefreshToken.class);
    org.mockito.Mockito.verify(refreshTokenRepository).save(saved.capture());
    assertThat(saved.getValue().getTokenHash()).isEqualTo(expectedHash);
    when(refreshTokenRepository.findByTokenHash(any()))
        .thenReturn(java.util.Optional.of(saved.getValue()));

    service.revoke(rawToken, "member-uuid");

    ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
    verify(refreshTokenRepository).findByTokenHash(tokenHash.capture());
    assertThat(tokenHash.getValue()).isEqualTo(expectedHash);
    assertThat(saved.getValue().getRevokedAt()).isNotNull();
    assertThatThrownBy(() -> service.refresh(rawToken)).isInstanceOf(CustomException.class);
  }

  @Test
  void ignoresMissingInactiveOrUnownedTokenDuringLogoutRevocation() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    assertThatCode(() -> service.revoke(null, "member-uuid")).doesNotThrowAnyException();
    assertThatCode(() -> service.revoke(" ", "member-uuid")).doesNotThrowAnyException();
    verifyNoInteractions(refreshTokenRepository);

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(java.util.Optional.empty());
    assertThatCode(() -> service.revoke("unknown-token", "member-uuid")).doesNotThrowAnyException();
  }

  @Test
  void revokesPresentedRefreshTokenWithoutMemberUuid() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    Member member = mock(Member.class);
    when(refreshTokenRepository.save(any(MemberRefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String rawToken = service.issue(member);
    ArgumentCaptor<MemberRefreshToken> saved = ArgumentCaptor.forClass(MemberRefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(saved.getValue()));

    service.revoke(rawToken, null);

    assertThat(saved.getValue().getRevokedAt()).isNotNull();
  }

  @Test
  void revokesPresentedRefreshTokenWithBlankMemberUuid() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    Member member = mock(Member.class);
    when(refreshTokenRepository.save(any(MemberRefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String rawToken = service.issue(member);
    ArgumentCaptor<MemberRefreshToken> saved = ArgumentCaptor.forClass(MemberRefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(saved.getValue()));

    service.revoke(rawToken, " ");

    assertThat(saved.getValue().getRevokedAt()).isNotNull();
  }

  @Test
  void doesNotRevokeAnActiveTokenOwnedByAnotherMember() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    Member member = mock(Member.class);
    when(member.getUuid()).thenReturn("member-uuid");
    when(refreshTokenRepository.save(any(MemberRefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String rawToken = service.issue(member);
    ArgumentCaptor<MemberRefreshToken> saved = ArgumentCaptor.forClass(MemberRefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(saved.getValue()));

    service.revoke(rawToken, "another-member-uuid");

    assertThat(saved.getValue().getRevokedAt()).isNull();
  }

  @Test
  void recognizesOnlyActiveRefreshTokensOwnedByTheSuppliedMember() {
    Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    SecurityProperties properties = properties();
    RefreshTokenService service =
        new RefreshTokenService(
            refreshTokenRepository, new JwtService(properties), properties, clock);
    Member member = mock(Member.class);
    when(member.getUuid()).thenReturn("member-uuid");
    when(refreshTokenRepository.save(any(MemberRefreshToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String rawToken = service.issue(member);
    ArgumentCaptor<MemberRefreshToken> saved = ArgumentCaptor.forClass(MemberRefreshToken.class);
    verify(refreshTokenRepository).save(saved.capture());

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThat(service.isActiveForMember(rawToken, "member-uuid")).isFalse();

    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(saved.getValue()));
    assertThat(service.isActiveForMember(rawToken, "member-uuid")).isTrue();
    assertThat(service.isActiveForMember(rawToken, "another-member-uuid")).isFalse();
    assertThat(service.isActiveForMember(null, "member-uuid")).isFalse();

    MemberRefreshToken expired =
        MemberRefreshToken.create(
            member,
            saved.getValue().getTokenHash(),
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));
    assertThat(service.isActiveForMember(rawToken, "member-uuid")).isFalse();

    MemberRefreshToken revoked =
        MemberRefreshToken.create(
            member,
            saved.getValue().getTokenHash(),
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1));
    revoked.revoke();
    when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked));
    assertThat(service.isActiveForMember(rawToken, "member-uuid")).isFalse();
  }

  private SecurityProperties properties() {
    return new SecurityProperties(
        "issuer",
        "01234567890123456789012345678901",
        "readle-api",
        30,
        14,
        "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=",
        10,
        "http://localhost:8080",
        List.of("/"),
        new SecurityProperties.OAuthProviders(
            new SecurityProperties.OAuthProviderSettings("", "", "", "", ""),
            new SecurityProperties.OAuthProviderSettings("", "", "", "", "")));
  }
}
