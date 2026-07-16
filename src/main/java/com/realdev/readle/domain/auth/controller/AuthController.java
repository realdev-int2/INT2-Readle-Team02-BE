package com.realdev.readle.domain.auth.controller;

import com.realdev.readle.domain.auth.OAuthStateCookie;
import com.realdev.readle.domain.auth.RefreshTokenCookie;
import com.realdev.readle.domain.auth.service.AuthService;
import com.realdev.readle.domain.auth.service.RefreshTokenService;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;
  private final SecurityProperties properties;

  public AuthController(
      AuthService authService,
      RefreshTokenService refreshTokenService,
      SecurityProperties properties) {
    this.authService = authService;
    this.refreshTokenService = refreshTokenService;
    this.properties = properties;
  }

  @GetMapping("/auth/{provider}/start")
  public ResponseEntity<Void> start(
      @PathVariable String provider, @RequestParam(required = false) String returnTo) {
    AuthService.StartResult result = authService.start(provider, returnTo);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(java.net.URI.create(result.authorizationUrl()))
        .header(
            HttpHeaders.SET_COOKIE,
            OAuthStateCookie.create(result.state(), properties.stateMinutes()).toString())
        .build();
  }

  @GetMapping("/auth/{provider}/callback")
  public ResponseEntity<Void> callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @CookieValue(value = OAuthStateCookie.NAME, required = false) String browserState) {
    requireMatchingBrowserState(state, browserState);
    AuthService.CallbackResult result = authService.callback(provider, code, state);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(java.net.URI.create(result.returnTo()))
        .header(
            HttpHeaders.SET_COOKIE,
            RefreshTokenCookie.create(result.refreshToken(), properties.refreshTokenDays())
                .toString())
        .header(HttpHeaders.SET_COOKIE, OAuthStateCookie.delete().toString())
        .build();
  }

  @PostMapping("/auth/refresh")
  public ApiResponse<Map<String, String>> refresh(
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    return new ApiResponse<>(Map.of("accessToken", refreshTokenService.refresh(refreshToken)));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      Authentication authentication,
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    String memberUuid =
        authentication == null || authentication.getPrincipal() == null
            ? null
            : String.valueOf(authentication.getPrincipal());
    refreshTokenService.revoke(refreshToken, memberUuid);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.delete().toString())
        .build();
  }

  @GetMapping("/auth/session")
  public ApiResponse<SessionResponse> session(
      Authentication authentication,
      HttpServletRequest request,
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
    boolean authenticated =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    String uuid = authenticated ? String.valueOf(authentication.getPrincipal()) : null;
    authenticated = authenticated && refreshTokenService.isActiveForMember(refreshToken, uuid);
    return new ApiResponse<>(new SessionResponse(authenticated, authenticated ? uuid : null));
  }

  @GetMapping("/users/me")
  public ApiResponse<CurrentUserResponse> currentUser(Authentication authentication) {
    String uuid = String.valueOf(authentication.getPrincipal());
    Member member = authService.currentMember(uuid);
    return new ApiResponse<>(new CurrentUserResponse(member.getUuid(), member.getNickname()));
  }

  public record ApiResponse<T>(T data) {}

  public record SessionResponse(boolean authenticated, String uuid) {}

  public record CurrentUserResponse(String uuid, String nickname) {}

  private void requireMatchingBrowserState(String state, String browserState) {
    if (state == null
        || browserState == null
        || !MessageDigest.isEqual(
            state.getBytes(StandardCharsets.UTF_8),
            browserState.getBytes(StandardCharsets.UTF_8))) {
      throw new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }
  }
}
