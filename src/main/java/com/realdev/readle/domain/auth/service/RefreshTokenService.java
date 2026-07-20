package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.MemberRefreshToken;
import com.realdev.readle.domain.member.repository.MemberRefreshTokenRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final MemberRefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final SecurityProperties properties;
  private final Clock clock;

  @Autowired
  public RefreshTokenService(
      MemberRefreshTokenRepository refreshTokenRepository,
      JwtService jwtService,
      SecurityProperties properties) {
    this(refreshTokenRepository, jwtService, properties, Clock.systemUTC());
  }

  RefreshTokenService(
      MemberRefreshTokenRepository refreshTokenRepository,
      JwtService jwtService,
      SecurityProperties properties,
      Clock clock) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtService = jwtService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public String issue(Member member) {
    String rawToken = randomToken();
    refreshTokenRepository.save(
        MemberRefreshToken.create(
            member, hash(rawToken), now().plusDays(properties.refreshTokenDays())));
    return rawToken;
  }

  @Transactional(readOnly = true)
  public String refresh(String rawToken) {
    MemberRefreshToken token = activeToken(rawToken);
    return jwtService.issue(token.getMember().getUuid());
  }

  @Transactional(readOnly = true)
  public boolean isActiveForMember(String rawToken, String memberUuid) {
    if (rawToken == null || rawToken.isBlank() || memberUuid == null || memberUuid.isBlank()) {
      return false;
    }
    return findActiveToken(rawToken)
        .map(MemberRefreshToken::getMember)
        .map(Member::getUuid)
        .filter(memberUuid::equals)
        .isPresent();
  }

  @Transactional
  public void revoke(String rawToken, String memberUuid) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    findActiveToken(rawToken)
        .filter(
            token ->
                memberUuid == null
                    || memberUuid.isBlank()
                    || memberUuid.equals(token.getMember().getUuid()))
        .ifPresent(MemberRefreshToken::revoke);
  }

  private MemberRefreshToken activeToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalidRefreshToken();
    }
    return findActiveToken(rawToken).orElseThrow(this::invalidRefreshToken);
  }

  private Optional<MemberRefreshToken> findActiveToken(String rawToken) {
    return refreshTokenRepository
        .findByTokenHash(hash(rawToken))
        .filter(token -> token.getRevokedAt() == null && token.getExpiresAt().isAfter(now()));
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private CustomException invalidRefreshToken() {
    return new CustomException(GlobalErrorCode.INVALID_REFRESH_TOKEN);
  }
}
