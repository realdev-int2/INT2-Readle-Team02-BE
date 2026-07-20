package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class OAuthProviderClientTest {

  private OAuthProviderClient client;

  @BeforeEach
  void setUp() {
    client = new OAuthProviderClient(RestClient.builder().build(), properties());
  }

  @Test
  void mapsBlankAuthorizationCodeToOAuthFailure() {
    assertThatThrownBy(
            () ->
                client.exchange(
                    OAuthProvider.GOOGLE,
                    " ",
                    "verifier",
                    "https://readle.test/api/auth/google/callback"))
        .isInstanceOf(com.realdev.readle.global.exception.CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  @Test
  void mapsMissingGoogleConfigurationToOAuthFailure() {
    OAuthProviderClient unconfiguredClient =
        new OAuthProviderClient(
            RestClient.builder().build(), propertiesWithMissingGoogleConfiguration());

    assertThatThrownBy(
            () ->
                unconfiguredClient.exchange(
                    OAuthProvider.GOOGLE,
                    "code",
                    "verifier",
                    "https://readle.test/api/auth/google/callback"))
        .isInstanceOf(com.realdev.readle.global.exception.CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  @Test
  void mapsMissingGoogleConfigurationToOAuthFailureWhenBuildingAuthorizationUrl() {
    OAuthProviderClient unconfiguredClient =
        new OAuthProviderClient(
            RestClient.builder().build(), propertiesWithMissingGoogleConfiguration());

    assertThatThrownBy(
            () ->
                unconfiguredClient.authorizationUrl(
                    OAuthProvider.GOOGLE,
                    "state",
                    "code-challenge",
                    "https://readle.test/api/auth/google/callback"))
        .isInstanceOf(com.realdev.readle.global.exception.CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  @Test
  void mapsMissingGoogleConfigurationToOAuthFailureWhenCheckingConfiguration() {
    OAuthProviderClient unconfiguredClient =
        new OAuthProviderClient(RestClient.builder().build(), propertiesWithNullOAuth());

    assertThatThrownBy(() -> unconfiguredClient.requireConfigured(OAuthProvider.GOOGLE))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }

  @Test
  void buildsGoogleAuthorizationUrlWithConfiguredPkceAndScopeParameters() {
    String authorizationUrl =
        client.authorizationUrl(
            OAuthProvider.GOOGLE,
            "google-state",
            "google-code-challenge",
            "https://readle.test/api/auth/google/callback");

    var query = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams();
    assertThat(authorizationUrl).startsWith("https://accounts.google.test/oauth2/auth");
    assertThat(query).containsEntry("response_type", List.of("code"));
    assertThat(query).containsEntry("client_id", List.of("google-client-id"));
    assertThat(query)
        .containsEntry("redirect_uri", List.of("https://readle.test/api/auth/google/callback"));
    assertThat(query).containsEntry("state", List.of("google-state"));
    assertThat(query).containsEntry("code_challenge", List.of("google-code-challenge"));
    assertThat(query).containsEntry("code_challenge_method", List.of("S256"));
    assertThat(UriUtils.decode(query.getFirst("scope"), StandardCharsets.UTF_8))
        .isEqualTo("openid profile email");
  }

  @Test
  void buildsKakaoAuthorizationUrlWithConfiguredPkceAndScopeParameters() {
    String authorizationUrl =
        client.authorizationUrl(
            OAuthProvider.KAKAO,
            "kakao-state",
            "kakao-code-challenge",
            "https://readle.test/api/auth/kakao/callback");

    var query = UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams();
    assertThat(authorizationUrl).startsWith("https://kauth.kakao.test/oauth/authorize");
    assertThat(query).containsEntry("response_type", List.of("code"));
    assertThat(query).containsEntry("client_id", List.of("kakao-client-id"));
    assertThat(query)
        .containsEntry("redirect_uri", List.of("https://readle.test/api/auth/kakao/callback"));
    assertThat(query).containsEntry("state", List.of("kakao-state"));
    assertThat(query).containsEntry("code_challenge", List.of("kakao-code-challenge"));
    assertThat(query).containsEntry("code_challenge_method", List.of("S256"));
    assertThat(UriUtils.decode(query.getFirst("scope"), StandardCharsets.UTF_8))
        .isEqualTo("profile_nickname profile_image account_email");
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
        "https://readle.test",
        List.of("/", "/dashboard"),
        new SecurityProperties.OAuthProviders(
            new SecurityProperties.OAuthProviderSettings(
                "google-client-id",
                "google-client-secret",
                "https://accounts.google.test/oauth2/auth",
                "https://oauth2.google.test/token",
                "https://openidconnect.google.test/userinfo"),
            new SecurityProperties.OAuthProviderSettings(
                "kakao-client-id",
                "kakao-client-secret",
                "https://kauth.kakao.test/oauth/authorize",
                "https://kauth.kakao.test/oauth/token",
                "https://kapi.kakao.test/v2/user/me")));
  }

  private SecurityProperties propertiesWithMissingGoogleConfiguration() {
    SecurityProperties configured = properties();
    return new SecurityProperties(
        configured.jwtIssuer(),
        configured.jwtSecret(),
        configured.jwtAudience(),
        configured.accessTokenMinutes(),
        configured.refreshTokenDays(),
        configured.stateEncryptionKey(),
        configured.stateMinutes(),
        configured.backendOrigin(),
        configured.allowedReturnPaths(),
        new SecurityProperties.OAuthProviders(
            new SecurityProperties.OAuthProviderSettings("", "", "", "", ""),
            configured.oauth().kakao()));
  }

  private SecurityProperties propertiesWithNullOAuth() {
    return new SecurityProperties(
        "issuer",
        "01234567890123456789012345678901",
        "readle-api",
        30,
        14,
        "MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=",
        10,
        "https://readle.test",
        List.of("/", "/dashboard"),
        null);
  }
}
