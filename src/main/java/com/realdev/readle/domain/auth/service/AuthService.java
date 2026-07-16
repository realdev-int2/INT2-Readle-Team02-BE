package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.member.service.OAuthMemberService;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final OAuthStateService stateService;
  private final OAuthProviderClient providerClient;
  private final OAuthMemberService memberService;
  private final MemberRepository memberRepository;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties properties;

  public AuthService(
      OAuthStateService stateService,
      OAuthProviderClient providerClient,
      OAuthMemberService memberService,
      MemberRepository memberRepository,
      RefreshTokenService refreshTokenService,
      SecurityProperties properties) {
    this.stateService = stateService;
    this.providerClient = providerClient;
    this.memberService = memberService;
    this.memberRepository = memberRepository;
    this.refreshTokenService = refreshTokenService;
    this.properties = properties;
  }

  public StartResult start(String providerName, String returnTo) {
    OAuthProvider provider = provider(providerName);
    providerClient.requireConfigured(provider);
    OAuthStateService.OAuthStart state = stateService.create(provider, returnTo);
    return new StartResult(
        providerClient.authorizationUrl(
            provider, state.state(), state.codeChallenge(), callbackUri(provider)),
        state.state());
  }

  public CallbackResult callback(String providerName, String code, String state) {
    OAuthProvider provider = provider(providerName);
    OAuthStateService.ConsumedOAuthState consumed = stateService.consume(provider, state);
    OAuthProfile profile =
        providerClient.exchange(provider, code, consumed.codeVerifier(), callbackUri(provider));
    Member member = memberService.upsert(profile);
    return new CallbackResult(consumed.returnTo(), refreshTokenService.issue(member));
  }

  public Member currentMember(String uuid) {
    return memberRepository
        .findByUuid(uuid)
        .orElseThrow(() -> new CustomException(GlobalErrorCode.UNAUTHORIZED));
  }

  private OAuthProvider provider(String providerName) {
    if (providerName == null) {
      throw failure();
    }
    try {
      return OAuthProvider.valueOf(providerName.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw failure();
    }
  }

  private String callbackUri(OAuthProvider provider) {
    return properties.backendOrigin()
        + "/api/auth/"
        + provider.name().toLowerCase(java.util.Locale.ROOT)
        + "/callback";
  }

  private CustomException failure() {
    return new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  public record CallbackResult(String returnTo, String refreshToken) {}

  public record StartResult(String authorizationUrl, String state) {}
}
