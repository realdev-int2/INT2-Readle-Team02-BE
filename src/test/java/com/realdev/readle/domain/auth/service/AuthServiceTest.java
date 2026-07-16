package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.member.service.OAuthMemberService;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private OAuthStateService stateService;
  @Mock private OAuthProviderClient providerClient;
  @Mock private OAuthMemberService memberService;
  @Mock private MemberRepository memberRepository;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private SecurityProperties properties;

  @InjectMocks private AuthService authService;

  @Test
  void completesCallbackWithNullEmailProfileBeforeIssuingRefreshToken() {
    OAuthStateService.ConsumedOAuthState consumed =
        new OAuthStateService.ConsumedOAuthState("/library", "code-verifier");
    OAuthProfile profile = new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    Member member = Member.create(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    when(properties.backendOrigin()).thenReturn("https://api.readle.test");
    when(stateService.consume(OAuthProvider.GOOGLE, "state")).thenReturn(consumed);
    when(providerClient.exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback"))
        .thenReturn(profile);
    when(memberService.upsert(profile)).thenReturn(member);
    when(refreshTokenService.issue(member)).thenReturn("refresh-token");

    AuthService.CallbackResult result =
        authService.callback("google", "authorization-code", "state");

    ArgumentCaptor<OAuthProfile> upsertedProfile = ArgumentCaptor.forClass(OAuthProfile.class);
    InOrder ordered = inOrder(stateService, providerClient, memberService, refreshTokenService);
    ordered.verify(stateService).consume(OAuthProvider.GOOGLE, "state");
    ordered
        .verify(providerClient)
        .exchange(
            OAuthProvider.GOOGLE,
            "authorization-code",
            "code-verifier",
            "https://api.readle.test/api/auth/google/callback");
    ordered.verify(memberService).upsert(upsertedProfile.capture());
    ordered.verify(refreshTokenService).issue(member);
    assertThat(upsertedProfile.getValue().email()).isNull();
    assertThat(result.returnTo()).isEqualTo("/library");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
  }

  @Test
  void rejectsUnsupportedProviderBeforeCallbackWork() {
    assertThatThrownBy(() -> authService.callback("unsupported", "authorization-code", "state"))
        .isInstanceOf(CustomException.class);

    verifyNoInteractions(
        stateService, providerClient, memberService, refreshTokenService, properties);
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

    verifyNoInteractions(memberService, refreshTokenService);
  }
}
