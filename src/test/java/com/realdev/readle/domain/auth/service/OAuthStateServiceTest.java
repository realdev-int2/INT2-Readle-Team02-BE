package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.auth.entity.OAuthAuthorizationState;
import com.realdev.readle.domain.auth.repository.OAuthAuthorizationStateRepository;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import jakarta.persistence.PessimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuthStateServiceTest {

  @Mock private OAuthAuthorizationStateRepository stateRepository;

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
  private final SecurityProperties properties =
      new SecurityProperties(
          "issuer",
          "01234567890123456789012345678901",
          "readle-api",
          30,
          14,
          "abcdefghijklmnopqrstuvwxyz123456",
          10,
          "http://localhost:8080",
          List.of("/", "/dashboard", "/quizzes/**"),
          new SecurityProperties.OAuthProviders(
              new SecurityProperties.OAuthProviderSettings("", "", "", "", ""),
              new SecurityProperties.OAuthProviderSettings("", "", "", "", "")));

  @Test
  void consumesStateOnceAndRejectsReplay() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthStateService.OAuthStart start = service.create(OAuthProvider.GOOGLE, "/dashboard");
    ArgumentCaptor<OAuthAuthorizationState> saved =
        ArgumentCaptor.forClass(OAuthAuthorizationState.class);
    verify(stateRepository).save(saved.capture());
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenReturn(Optional.of(saved.getValue()));

    OAuthStateService.ConsumedOAuthState consumed =
        service.consume(OAuthProvider.GOOGLE, start.state());

    assertThat(consumed.returnTo()).isEqualTo("/dashboard");
    assertThat(consumed.codeVerifier()).isNotBlank();
    verify(stateRepository).delete(saved.getValue());
    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, start.state()))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void mapsLockAcquisitionFailuresToOAuthFailure() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenThrow(new PessimisticLockException());

    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, "state"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);

    reset(stateRepository);
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenThrow(new CannotAcquireLockException("lock unavailable"));

    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, "state"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  @Test
  void rejectsExpiredState() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthAuthorizationState expired =
        OAuthAuthorizationState.create(
            "state-hash",
            OAuthProvider.GOOGLE,
            "/dashboard",
            "unused-ciphertext",
            LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusNanos(1));
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, "state"))
        .isInstanceOf(CustomException.class);

    verify(stateRepository).delete(expired);
  }

  @Test
  void rejectsStateRequestedForDifferentProvider() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthStateService.OAuthStart start = service.create(OAuthProvider.GOOGLE, "/dashboard");
    when(stateRepository.findByStateHashAndOauthProvider(any(), eq(OAuthProvider.KAKAO)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.consume(OAuthProvider.KAKAO, start.state()))
        .isInstanceOf(CustomException.class);

    verify(stateRepository).findByStateHashAndOauthProvider(any(), eq(OAuthProvider.KAKAO));
  }

  @Test
  void rejectsVerifierCiphertextAuthenticatedForDifferentProviderWithoutReturningVerifier() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthStateService.OAuthStart start = service.create(OAuthProvider.GOOGLE, "/dashboard");
    ArgumentCaptor<OAuthAuthorizationState> saved =
        ArgumentCaptor.forClass(OAuthAuthorizationState.class);
    verify(stateRepository).save(saved.capture());
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenReturn(Optional.of(saved.getValue()));

    assertThatThrownBy(() -> service.consume(OAuthProvider.KAKAO, start.state()))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void rejectsTamperedVerifierCiphertext() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthStateService.OAuthStart start = service.create(OAuthProvider.GOOGLE, "/dashboard");
    ArgumentCaptor<OAuthAuthorizationState> saved =
        ArgumentCaptor.forClass(OAuthAuthorizationState.class);
    verify(stateRepository).save(saved.capture());
    ReflectionTestUtils.setField(saved.getValue(), "codeVerifierCiphertext", "tampered-ciphertext");
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenReturn(Optional.of(saved.getValue()));

    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, start.state()))
        .isInstanceOf(CustomException.class);

    verify(stateRepository).delete(saved.getValue());
  }

  @Test
  void rejectsMalformedVerifierCiphertextThatCausesProviderException() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);
    OAuthStateService.OAuthStart start = service.create(OAuthProvider.GOOGLE, "/dashboard");
    ArgumentCaptor<OAuthAuthorizationState> saved =
        ArgumentCaptor.forClass(OAuthAuthorizationState.class);
    verify(stateRepository).save(saved.capture());
    ReflectionTestUtils.setField(saved.getValue(), "codeVerifierCiphertext", "AAAAAAAAAAAAAAAAAA");
    when(stateRepository.findByStateHashAndOauthProvider(any(), any()))
        .thenReturn(Optional.of(saved.getValue()));

    assertThatThrownBy(() -> service.consume(OAuthProvider.GOOGLE, start.state()))
        .isInstanceOf(CustomException.class);
  }

  @Test
  void preservesQueryForAllowlistedPathPattern() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);

    assertThat(service.safeReturnTo("/quizzes/123?from=dashboard"))
        .isEqualTo("/quizzes/123?from=dashboard");
  }

  @Test
  void normalizesInvalidOrUnmatchedReturnPathsToRoot() {
    OAuthStateService service = new OAuthStateService(stateRepository, properties, clock);

    assertThat(service.safeReturnTo("https://evil.example")).isEqualTo("/");
    assertThat(service.safeReturnTo("///evil")).isEqualTo("/");
    assertThat(service.safeReturnTo("/not-allowlisted")).isEqualTo("/");
    assertThat(service.safeReturnTo("/%2F%2Fevil")).isEqualTo("/");
  }

  @Test
  void rejectsStandaloneCatchAllReturnPathPattern() {
    SecurityProperties invalidProperties =
        new SecurityProperties(
            "issuer",
            "01234567890123456789012345678901",
            "readle-api",
            30,
            14,
            "abcdefghijklmnopqrstuvwxyz123456",
            10,
            "http://localhost:8080",
            List.of("/**"),
            properties.oauth());

    assertThatThrownBy(() -> new OAuthStateService(stateRepository, invalidProperties, clock))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
