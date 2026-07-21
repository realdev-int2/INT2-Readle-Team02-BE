package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final OAuthStateService stateService;
  private final OAuthProviderClient providerClient;
  private final OAuthLoginService loginService;
  private final MemberRepository memberRepository;
  private final SecurityProperties properties;

  public AuthService(
      OAuthStateService stateService,
      OAuthProviderClient providerClient,
      MemberRepository memberRepository,
      OAuthLoginService loginService,
      SecurityProperties properties) {
    this.stateService = stateService;
    this.providerClient = providerClient;
    this.memberRepository = memberRepository;
    this.loginService = loginService;
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
    OAuthProfile profile;
    try {
      profile =
          providerClient.exchange(provider, code, consumed.codeVerifier(), callbackUri(provider));
    } catch (CustomException exception) {
      if (exception.getErrorCode() != GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED) {
        throw exception;
      }
      throw new CallbackExchangeFailure(consumed.returnTo());
    }
    String refreshToken = loginService.login(profile);
    return new CallbackResult(consumed.returnTo(), refreshToken);
  }

  public String callbackFailure(String providerName, String state) {
    return stateService.consumeReturnTo(provider(providerName), state);
  }

  @Transactional(readOnly = true)
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

  public static final class CallbackExchangeFailure extends RuntimeException {

    private final String returnTo;

    public CallbackExchangeFailure(String returnTo) {
      super("OAuth provider exchange failed");
      this.returnTo = returnTo;
    }

    public String returnTo() {
      return returnTo;
    }
  }

  public record StartResult(String authorizationUrl, String state) {}
}
