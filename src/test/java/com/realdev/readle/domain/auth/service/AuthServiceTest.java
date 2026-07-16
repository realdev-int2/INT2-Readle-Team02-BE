package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private OAuthStateService stateService;
  @Mock private OAuthProviderClient providerClient;
  @Mock private MemberRepository memberRepository;
  @Mock private OAuthLoginService loginService;
  @Mock private SecurityProperties properties;

  @InjectMocks private AuthService authService;

  @Test
  void startsProviderAuthorizationWithStateAndConfiguredCallback() {
    OAuthStateService.OAuthStart state = new OAuthStateService.OAuthStart("state-abc", "challenge");
    when(properties.backendOrigin()).thenReturn("https://api.readle.test");
    when(stateService.create(OAuthProvider.GOOGLE, "/library")).thenReturn(state);
    when(providerClient.authorizationUrl(
            OAuthProvider.GOOGLE,
            "state-abc",
            "challenge",
            "https://api.readle.test/api/auth/google/callback"))
        .thenReturn("https://accounts.example.com/authorize");

    AuthService.StartResult result = authService.start("GoOgLe", "/library");

    verify(stateService).create(OAuthProvider.GOOGLE, "/library");
    verify(providerClient).requireConfigured(OAuthProvider.GOOGLE);
    verify(providerClient)
        .authorizationUrl(
            OAuthProvider.GOOGLE,
            "state-abc",
            "challenge",
            "https://api.readle.test/api/auth/google/callback");
    assertThat(result.authorizationUrl()).isEqualTo("https://accounts.example.com/authorize");
    assertThat(result.state()).isEqualTo("state-abc");
  }

  @Test
  void completesCallbackWithProfileThroughOAuthLoginService() {
    OAuthStateService.ConsumedOAuthState consumed =
        new OAuthStateService.ConsumedOAuthState("/library", "code-verifier");
    OAuthProfile profile = new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    when(properties.backendOrigin()).thenReturn("https://api.readle.test");
    when(stateService.consume(OAuthProvider.GOOGLE, "state")).thenReturn(consumed);
    when(providerClient.exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback"))
        .thenReturn(profile);
    when(loginService.login(profile)).thenReturn("refresh-token");

    AuthService.CallbackResult result =
        authService.callback("google", "authorization-code", "state");

    InOrder ordered = inOrder(stateService, providerClient, loginService);
    ordered.verify(stateService).consume(OAuthProvider.GOOGLE, "state");
    ordered
        .verify(providerClient)
        .exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback");
    ordered.verify(loginService).login(profile);
    assertThat(result.returnTo()).isEqualTo("/library");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void rejectsUnsupportedProviderBeforeCallbackWork() {
    assertThatThrownBy(() -> authService.callback("unsupported", "authorization-code", "state"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);

    verifyNoInteractions(stateService, providerClient, loginService, properties);
  }

  @Test
  void propagatesProviderExchangeFailureWithoutUpsertingMemberOrIssuingRefreshToken() {
    OAuthStateService.ConsumedOAuthState consumed =
        new OAuthStateService.ConsumedOAuthState("/library", "code-verifier");
    RuntimeException exchangeFailure = new RuntimeException("provider exchange failed");
    when(properties.backendOrigin()).thenReturn("https://api.readle.test");
    when(stateService.consume(OAuthProvider.GOOGLE, "state")).thenReturn(consumed);
    when(providerClient.exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback"))
        .thenThrow(exchangeFailure);

    assertThatThrownBy(() -> authService.callback("google", "authorization-code", "state"))
        .isSameAs(exchangeFailure);

    verifyNoInteractions(loginService);
  }

  @Test
  void avoidsLoginFinalizationWhenMemberUpsertFailsAfterStateConsumptionAndExchange() {
    OAuthStateService.ConsumedOAuthState consumed =
        new OAuthStateService.ConsumedOAuthState("/library", "code-verifier");
    OAuthProfile profile = new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    RuntimeException finalizeFailure = new RuntimeException("member upsert failed");
    when(properties.backendOrigin()).thenReturn("https://api.readle.test");
    when(stateService.consume(OAuthProvider.GOOGLE, "state")).thenReturn(consumed);
    when(providerClient.exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback"))
        .thenReturn(profile);
    when(loginService.login(profile)).thenThrow(finalizeFailure);

    assertThatThrownBy(() -> authService.callback("google", "authorization-code", "state"))
        .isSameAs(finalizeFailure);

    InOrder ordered = inOrder(stateService, providerClient, loginService);
    ordered.verify(stateService).consume(OAuthProvider.GOOGLE, "state");
    ordered
        .verify(providerClient)
        .exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback");
    ordered.verify(loginService).login(profile);
  }

  @Test
  void currentMemberReturnsStoredMemberForValidUuid() {
    Member member = Member.create(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    when(memberRepository.findByUuid("uuid")).thenReturn(Optional.of(member));

    assertThat(authService.currentMember("uuid")).isSameAs(member);

    verify(memberRepository).findByUuid("uuid");
  }

  @Test
  void currentMemberRejectsUnknownUuid() {
    when(memberRepository.findByUuid("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.currentMember("unknown"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);

    verify(memberRepository).findByUuid("unknown");
  }
}
