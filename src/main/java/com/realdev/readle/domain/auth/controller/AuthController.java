package com.realdev.readle.domain.auth.controller;

import com.realdev.readle.domain.auth.OAuthStateCookie;
import com.realdev.readle.domain.auth.RefreshTokenCookie;
import com.realdev.readle.domain.auth.dto.response.AccessTokenResponse;
import com.realdev.readle.domain.auth.service.AuthService;
import com.realdev.readle.domain.auth.service.RefreshTokenService;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.SecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@Tag(name = "Auth", description = "OAuth2/JWT 인증 API")
@Slf4j
public class AuthController {

  private static final String LOGIN_PATH = "/login";
  private static final String OAUTH_CANCELLED = "oauth_cancelled";
  private static final String OAUTH_FAILED = "oauth_failed";

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

  @Operation(summary = "OAuth 로그인 시작", description = "OAuth 제공자로 리디렉션하고 OAuth 상태 쿠키를 설정합니다.")
  @GetMapping("/auth/{provider}/start")
  public ResponseEntity<Void> start(
      @PathVariable String provider, @RequestParam(required = false) String returnTo) {
    try {
      AuthService.StartResult result = authService.start(provider, returnTo);
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(java.net.URI.create(result.authorizationUrl()))
          .header(
              HttpHeaders.SET_COOKIE,
              OAuthStateCookie.create(result.state(), properties.stateMinutes()).toString())
          .build();
    } catch (RuntimeException exception) {
      return oauthRuntimeFailureRedirect("start", provider, exception);
    }
  }

  @Operation(
      summary = "OAuth 로그인 콜백",
      description = "인증 코드를 처리하고 리프레시 토큰 쿠키를 설정한 뒤 반환 경로로 리디렉션합니다.")
  @GetMapping("/auth/{provider}/callback")
  public ResponseEntity<Void> callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      @CookieValue(value = OAuthStateCookie.NAME, required = false) String browserState) {
    if (!hasMatchingBrowserState(state, browserState)) {
      return oauthFailureRedirect(OAUTH_FAILED, null);
    }
    if (error != null || code == null || code.isBlank()) {
      try {
        return oauthFailureRedirect(
            "access_denied".equals(error) ? OAUTH_CANCELLED : OAUTH_FAILED,
            authService.callbackFailure(provider, state));
      } catch (RuntimeException exception) {
        return oauthRuntimeFailureRedirect("callback", provider, exception);
      }
    }
    try {
      AuthService.CallbackResult result = authService.callback(provider, code, state);
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(java.net.URI.create(result.returnTo()))
          .header(
              HttpHeaders.SET_COOKIE,
              RefreshTokenCookie.create(result.refreshToken(), properties.refreshTokenDays())
                  .toString())
          .header(HttpHeaders.SET_COOKIE, OAuthStateCookie.delete().toString())
          .build();
    } catch (AuthService.CallbackExchangeFailure exception) {
      return oauthFailureRedirect(OAUTH_FAILED, exception.returnTo());
    } catch (RuntimeException exception) {
      return oauthRuntimeFailureRedirect("callback", provider, exception);
    }
  }

  @Operation(summary = "Access Token 갱신", description = "리프레시 토큰 쿠키로 액세스 토큰을 발급합니다.")
  @PostMapping("/auth/refresh")
  public ApiResponse<AccessTokenResponse> refresh(
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    return new ApiResponse<>(new AccessTokenResponse(refreshTokenService.refresh(refreshToken)));
  }

  @Operation(summary = "로그아웃", description = "현재 리프레시 토큰을 폐기하고 쿠키를 삭제합니다.")
  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal String memberUuid,
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    refreshTokenService.revoke(refreshToken, memberUuid);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.delete().toString())
        .build();
  }

  @Operation(summary = "세션 상태 조회", description = "현재 액세스 토큰과 리프레시 토큰의 인증 상태를 조회합니다.")
  @GetMapping("/auth/session")
  public ApiResponse<SessionResponse> session(
      Authentication authentication,
      @AuthenticationPrincipal String memberUuid,
      HttpServletRequest request,
      @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
    ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
    boolean authenticated =
        authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    String uuid = authenticated ? memberUuid : null;
    authenticated = authenticated && refreshTokenService.isActiveForMember(refreshToken, uuid);
    return new ApiResponse<>(new SessionResponse(authenticated, authenticated ? uuid : null));
  }

  @Operation(summary = "현재 사용자 조회", description = "인증된 회원의 기본 정보를 조회합니다.")
  @GetMapping("/users/me")
  public ApiResponse<CurrentUserResponse> currentUser(@AuthenticationPrincipal String memberUuid) {
    if (memberUuid == null) {
      throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
    }
    Member member = authService.currentMember(memberUuid);
    return new ApiResponse<>(
        new CurrentUserResponse(
            member.getUuid(), member.getNickname(), member.getProfileImageUrl()));
  }

  public record ApiResponse<T>(T data) {}

  public record SessionResponse(boolean authenticated, String uuid) {}

  public record CurrentUserResponse(String uuid, String nickname, String profileImageUrl) {}

  private ResponseEntity<Void> oauthFailureRedirect(String error, String returnTo) {
    String location = LOGIN_PATH + "?authError=" + error;
    if (returnTo != null) {
      location += "&returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(java.net.URI.create(location))
        .header(HttpHeaders.SET_COOKIE, OAuthStateCookie.delete().toString())
        .build();
  }

  private ResponseEntity<Void> oauthRuntimeFailureRedirect(
      String flow, String provider, RuntimeException exception) {
    if (exception instanceof CustomException) {
      log.warn(
          "OAuth failure flow={} provider={} exception={}",
          flow,
          provider,
          exception.getClass().getSimpleName());
    } else {
      log.error(
          "OAuth failure flow={} provider={} exception={}",
          flow,
          provider,
          exception.getClass().getSimpleName());
    }
    return oauthFailureRedirect(OAUTH_FAILED, null);
  }

  private boolean hasMatchingBrowserState(String state, String browserState) {
    return state != null
        && browserState != null
        && !state.isBlank()
        && !browserState.isBlank()
        && MessageDigest.isEqual(
            state.getBytes(StandardCharsets.UTF_8), browserState.getBytes(StandardCharsets.UTF_8));
  }
}
