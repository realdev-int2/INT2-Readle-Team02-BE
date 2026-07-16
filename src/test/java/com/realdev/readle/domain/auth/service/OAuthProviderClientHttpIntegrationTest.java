package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class OAuthProviderClientHttpIntegrationTest {

  private static final String GOOGLE_TOKEN_URL = "https://oauth2.google.test/token";
  private static final String GOOGLE_USER_INFO_URL = "https://openidconnect.google.test/userinfo";
  private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.test/oauth/token";
  private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.test/v2/user/me";

  private MockRestServiceServer server;
  private OAuthProviderClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OAuthProviderClient(builder.build(), properties());
  }

  @Test
  void exchangesGoogleCodeWithSerializedFormAndMapsAllProfileFields() {
    expectTokenRequest(
        GOOGLE_TOKEN_URL,
        "google-client-id",
        "google-client-secret",
        "https://readle.test/api/auth/google/callback",
        "google-code",
        "google-verifier",
        "google-access-token");
    server
        .expect(requestTo(GOOGLE_USER_INFO_URL))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(AUTHORIZATION, "Bearer google-access-token"))
        .andRespond(
            withSuccess(
                "{\"sub\":\"google-sub\",\"email\":\"reader@example.com\","
                    + "\"name\":\"Readler\",\"picture\":\"https://img.test/readler\"}",
                MediaType.APPLICATION_JSON));

    OAuthProfile profile =
        client.exchange(
            OAuthProvider.GOOGLE,
            "google-code",
            "google-verifier",
            "https://readle.test/api/auth/google/callback");

    assertThat(profile)
        .extracting(
            OAuthProfile::provider,
            OAuthProfile::subject,
            OAuthProfile::email,
            OAuthProfile::displayName,
            OAuthProfile::profileImageUrl)
        .containsExactly(
            OAuthProvider.GOOGLE,
            "google-sub",
            "reader@example.com",
            "Readler",
            "https://img.test/readler");
  }

  @Test
  void allowsGoogleOptionalProfileFieldsToBeAbsent() {
    expectTokenRequest(
        GOOGLE_TOKEN_URL,
        "google-client-id",
        "google-client-secret",
        "https://readle.test/api/auth/google/callback",
        "code",
        "verifier",
        "access-token");
    expectUserInfo(GOOGLE_USER_INFO_URL, "Bearer access-token", "{\"sub\":\"google-sub\"}");

    OAuthProfile profile =
        client.exchange(
            OAuthProvider.GOOGLE,
            "code",
            "verifier",
            "https://readle.test/api/auth/google/callback");

    assertThat(profile.email()).isNull();
    assertThat(profile.displayName()).isNull();
    assertThat(profile.profileImageUrl()).isNull();
  }

  @Test
  void normalizesNumericKakaoIdentityAndAllowsAccountAndProfileFieldsToBeAbsent() {
    expectTokenRequest(
        KAKAO_TOKEN_URL,
        "kakao-client-id",
        "kakao-client-secret",
        "https://readle.test/api/auth/kakao/callback",
        "code",
        "verifier",
        "access-token");
    expectUserInfo(KAKAO_USER_INFO_URL, "Bearer access-token", "{\"id\":12345}");

    OAuthProfile profile =
        client.exchange(
            OAuthProvider.KAKAO, "code", "verifier", "https://readle.test/api/auth/kakao/callback");

    assertThat(profile.provider()).isEqualTo(OAuthProvider.KAKAO);
    assertThat(profile.subject()).isEqualTo("12345");
    assertThat(profile.email()).isNull();
    assertThat(profile.displayName()).isNull();
    assertThat(profile.profileImageUrl()).isNull();
  }

  @Test
  void normalizesStringKakaoIdentityAndMapsAccountAndProfileFields() {
    expectTokenRequest(
        KAKAO_TOKEN_URL,
        "kakao-client-id",
        "kakao-client-secret",
        "https://readle.test/api/auth/kakao/callback",
        "kakao-code",
        "kakao-verifier",
        "kakao-access-token");
    expectUserInfo(
        KAKAO_USER_INFO_URL,
        "Bearer kakao-access-token",
        "{\"id\":\"kakao-id\",\"kakao_account\":{\"email\":\"reader@example.com\","
            + "\"profile\":{\"nickname\":\"Readler\","
            + "\"thumbnail_image_url\":\"https://img.test/readler\"}}}");

    OAuthProfile profile =
        client.exchange(
            OAuthProvider.KAKAO,
            "kakao-code",
            "kakao-verifier",
            "https://readle.test/api/auth/kakao/callback");

    assertThat(profile)
        .extracting(
            OAuthProfile::provider,
            OAuthProfile::subject,
            OAuthProfile::email,
            OAuthProfile::displayName,
            OAuthProfile::profileImageUrl)
        .containsExactly(
            OAuthProvider.KAKAO,
            "kakao-id",
            "reader@example.com",
            "Readler",
            "https://img.test/readler");
  }

  @Test
  void rejectsMissingGoogleIdentityWithOnlyOAuthFailure() {
    assertGoogleIdentityFailure("{}");
  }

  @Test
  void rejectsBlankGoogleIdentityWithOnlyOAuthFailure() {
    assertGoogleIdentityFailure("{\"sub\":\"  \"}");
  }

  @Test
  void rejectsMissingKakaoIdentityWithOnlyOAuthFailure() {
    assertKakaoIdentityFailure("{}");
  }

  @Test
  void rejectsBlankKakaoIdentityWithOnlyOAuthFailure() {
    assertKakaoIdentityFailure("{\"id\":\" \"}");
  }

  @Test
  void rejectsMissingAccessTokenWithOnlyOAuthFailure() {
    server
        .expect(requestTo(GOOGLE_TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.GOOGLE,
                "code",
                "verifier",
                "https://readle.test/api/auth/google/callback"));
  }

  @Test
  void mapsTokenNon2xxToOnlyOAuthFailure() {
    server
        .expect(requestTo(GOOGLE_TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.GOOGLE,
                "code",
                "verifier",
                "https://readle.test/api/auth/google/callback"));
  }

  @Test
  void mapsTokenTransportFailureToOnlyOAuthFailure() {
    server
        .expect(requestTo(GOOGLE_TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withException(new IOException("transport failure")));

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.GOOGLE,
                "code",
                "verifier",
                "https://readle.test/api/auth/google/callback"));
  }

  @Test
  void mapsUserInfoNon2xxToOnlyOAuthFailure() {
    expectTokenRequest(
        GOOGLE_TOKEN_URL,
        "google-client-id",
        "google-client-secret",
        "https://readle.test/api/auth/google/callback",
        "code",
        "verifier",
        "access-token");
    server
        .expect(requestTo(GOOGLE_USER_INFO_URL))
        .andExpect(header(AUTHORIZATION, "Bearer access-token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.GOOGLE,
                "code",
                "verifier",
                "https://readle.test/api/auth/google/callback"));
  }

  private void assertGoogleIdentityFailure(String responseBody) {
    expectTokenRequest(
        GOOGLE_TOKEN_URL,
        "google-client-id",
        "google-client-secret",
        "https://readle.test/api/auth/google/callback",
        "code",
        "verifier",
        "access-token");
    expectUserInfo(GOOGLE_USER_INFO_URL, "Bearer access-token", responseBody);

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.GOOGLE,
                "code",
                "verifier",
                "https://readle.test/api/auth/google/callback"));
  }

  private void assertKakaoIdentityFailure(String responseBody) {
    expectTokenRequest(
        KAKAO_TOKEN_URL,
        "kakao-client-id",
        "kakao-client-secret",
        "https://readle.test/api/auth/kakao/callback",
        "code",
        "verifier",
        "access-token");
    expectUserInfo(KAKAO_USER_INFO_URL, "Bearer access-token", responseBody);

    assertOAuthFailure(
        () ->
            client.exchange(
                OAuthProvider.KAKAO,
                "code",
                "verifier",
                "https://readle.test/api/auth/kakao/callback"));
  }

  private void assertOAuthFailure(ThrowingCallable exchange) {
    try {
      assertThatThrownBy(exchange)
          .isInstanceOf(com.realdev.readle.global.exception.CustomException.class)
          .extracting("errorCode")
          .isEqualTo(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
    } finally {
      server.verify();
    }
  }

  private void expectUserInfo(String url, String authorization, String responseBody) {
    server
        .expect(requestTo(url))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(AUTHORIZATION, authorization))
        .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
  }

  private void expectTokenRequest(
      String tokenUrl,
      String clientId,
      String clientSecret,
      String redirectUri,
      String code,
      String verifier,
      String accessToken) {
    server
        .expect(requestTo(tokenUrl))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(
            content()
                .formData(
                    form(
                        "authorization_code", clientId, clientSecret, redirectUri, code, verifier)))
        .andRespond(
            withSuccess("{\"access_token\":\"" + accessToken + "\"}", MediaType.APPLICATION_JSON));
  }

  private MultiValueMap<String, String> form(
      String grantType,
      String clientId,
      String clientSecret,
      String redirectUri,
      String code,
      String verifier) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", grantType);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("redirect_uri", redirectUri);
    form.add("code", code);
    form.add("code_verifier", verifier);
    return form;
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
                GOOGLE_TOKEN_URL,
                GOOGLE_USER_INFO_URL),
            new SecurityProperties.OAuthProviderSettings(
                "kakao-client-id",
                "kakao-client-secret",
                "https://kauth.kakao.test/oauth/authorize",
                KAKAO_TOKEN_URL,
                KAKAO_USER_INFO_URL)));
  }
}
