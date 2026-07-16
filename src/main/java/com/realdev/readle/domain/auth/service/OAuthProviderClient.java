package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.service.OAuthProfile;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuthProviderClient {

  private final RestClient restClient;
  private final SecurityProperties properties;

  private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
      new ParameterizedTypeReference<>() {};

  public OAuthProviderClient(RestClient.Builder restClientBuilder, SecurityProperties properties) {
    this.restClient = restClientBuilder.build();
    this.properties = properties;
  }

  public String authorizationUrl(
      OAuthProvider provider, String state, String codeChallenge, String redirectUri) {
    SecurityProperties.OAuthProviderSettings settings = settings(provider);
    requireConfigured(settings);
    return UriComponentsBuilder.fromUriString(settings.authorizationUrl())
        .queryParam("response_type", "code")
        .queryParam("client_id", settings.clientId())
        .queryParam("redirect_uri", redirectUri)
        .queryParam("state", state)
        .queryParam("code_challenge", codeChallenge)
        .queryParam("code_challenge_method", "S256")
        .queryParam(
            "scope",
            provider == OAuthProvider.GOOGLE
                ? "openid profile email"
                : "profile_nickname account_email")
        .encode()
        .build()
        .toUriString();
  }

  public OAuthProfile exchange(
      OAuthProvider provider, String code, String verifier, String redirectUri) {
    if (code == null || code.isBlank()) {
      throw failure();
    }
    try {
      SecurityProperties.OAuthProviderSettings settings = settings(provider);
      requireConfigured(settings);
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "authorization_code");
      form.add("client_id", settings.clientId());
      form.add("client_secret", settings.clientSecret());
      form.add("redirect_uri", redirectUri);
      form.add("code", code);
      form.add("code_verifier", verifier);
      Map<?, ?> token =
          restClient
              .post()
              .uri(settings.tokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(JSON_OBJECT);
      String accessToken = value(token, "access_token");
      if (accessToken == null || accessToken.isBlank()) {
        throw failure();
      }
      Map<?, ?> userInfo =
          restClient
              .get()
              .uri(settings.userInfoUrl())
              .header("Authorization", "Bearer " + accessToken)
              .retrieve()
              .body(JSON_OBJECT);
      return profile(provider, userInfo);
    } catch (CustomException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw failure();
    }
  }

  private OAuthProfile profile(OAuthProvider provider, Map<?, ?> attributes) {
    if (provider == OAuthProvider.GOOGLE) {
      return requiredProfile(
          provider,
          value(attributes, "sub"),
          value(attributes, "email"),
          value(attributes, "name"),
          value(attributes, "picture"));
    }
    Map<?, ?> account = map(attributes == null ? null : attributes.get("kakao_account"));
    Map<?, ?> profile = map(account == null ? null : account.get("profile"));
    return requiredProfile(
        provider,
        value(attributes, "id"),
        value(account, "email"),
        value(profile, "nickname"),
        value(profile, "thumbnail_image_url"));
  }

  private OAuthProfile requiredProfile(
      OAuthProvider provider, String subject, String email, String displayName, String imageUrl) {
    if (subject == null || subject.isBlank()) {
      throw failure();
    }
    return new OAuthProfile(provider, subject, email, displayName, imageUrl);
  }

  private SecurityProperties.OAuthProviderSettings settings(OAuthProvider provider) {
    if (provider == OAuthProvider.GOOGLE) {
      return properties.oauth().google();
    }
    if (provider == OAuthProvider.KAKAO) {
      return properties.oauth().kakao();
    }
    throw failure();
  }

  private void requireConfigured(SecurityProperties.OAuthProviderSettings settings) {
    if (settings == null
        || isBlank(settings.clientId())
        || isBlank(settings.authorizationUrl())
        || isBlank(settings.tokenUrl())
        || isBlank(settings.userInfoUrl())) {
      throw failure();
    }
  }

  private String value(Map<?, ?> values, String key) {
    if (values == null || values.get(key) == null) {
      return null;
    }
    return String.valueOf(values.get(key));
  }

  private Map<?, ?> map(Object value) {
    return value instanceof Map<?, ?> map ? map : null;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private CustomException failure() {
    return new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
  }
}
