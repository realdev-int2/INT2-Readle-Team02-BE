package com.realdev.readle.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.auth.RefreshTokenCookie;
import com.realdev.readle.domain.auth.service.AuthService;
import com.realdev.readle.domain.auth.service.RefreshTokenService;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityErrorResponseWriter;
import com.realdev.readle.global.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

  private static final String STATE_COOKIE = "__Host-readle_oauth_state";

  @Autowired private MockMvc mockMvc;

  @MockBean private AuthService authService;
  @MockBean private RefreshTokenService refreshTokenService;
  @MockBean private JwtService jwtService;
  @MockBean private SecurityErrorResponseWriter securityErrorResponseWriter;
  @MockBean private SecurityProperties properties;

  @Test
  void startsOAuthWithBrowserBoundStateCookie() throws Exception {
    when(authService.start("google", "/"))
        .thenReturn(
            new AuthService.StartResult("https://accounts.example/authorize", "state-value"));
    when(properties.stateMinutes()).thenReturn(10);

    mockMvc
        .perform(get("/api/auth/google/start").param("returnTo", "/"))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(STATE_COOKIE)))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("state-value")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
  }

  @Test
  void rejectsCallbackWithoutMatchingBrowserStateBeforeTokenIssuance() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "different-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verify(authService, never()).callback("google", "authorization-code", "expected-state");
    verify(authService, never()).callbackFailure("google", "expected-state");
  }

  @Test
  void rejectsCallbackWithoutBrowserStateBeforeTokenIssuance() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state"))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verify(authService, never()).callback("google", "authorization-code", "expected-state");
    verify(authService, never()).callbackFailure("google", "expected-state");
  }

  @Test
  void rejectsCallbackWithBlankStateParameterBeforeTokenIssuance() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verifyNoInteractions(authService);
  }

  @Test
  void rejectsCallbackWithBlankBrowserStateCookieBeforeTokenIssuance() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verifyNoInteractions(authService);
  }

  @Test
  void redirectsAccessDeniedCallbackWithCancellationCodeAfterConsumingMatchingState()
      throws Exception {
    when(authService.callbackFailure("google", "expected-state")).thenReturn("/dashboard");

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("error", "access_denied")
                .param("error_description", "provider cancellation detail")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION, "/login?authError=oauth_cancelled&returnTo=%2Fdashboard"))
        .andExpect(
            header()
                .stringValues(HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))))
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                    .noneMatch(cookie -> cookie.contains(RefreshTokenCookie.NAME)));

    verify(authService).callbackFailure("google", "expected-state");
    verify(authService, never()).callback("google", null, "expected-state");
  }

  @Test
  void redirectsGenericProviderErrorWithOnlyStoredSafeReturnTo() throws Exception {
    when(authService.callbackFailure("google", "expected-state"))
        .thenReturn("/quizzes/123?from=dashboard");

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("error", "provider_error")
                .param("error_description", "raw provider detail")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    "/login?authError=oauth_failed&returnTo=%2Fquizzes%2F123%3Ffrom%3Ddashboard"));

    verify(authService).callbackFailure("google", "expected-state");
    verify(authService, never()).callback("google", null, "expected-state");
  }

  @Test
  void redirectsMissingCodeWithOnlyStoredSafeReturnTo() throws Exception {
    when(authService.callbackFailure("google", "expected-state")).thenReturn("/dashboard");

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION, "/login?authError=oauth_failed&returnTo=%2Fdashboard"));

    verify(authService).callbackFailure("google", "expected-state");
    verify(authService, never()).callback("google", null, "expected-state");
  }

  @Test
  void redirectsUnusableStoredStateToFixedLogin() throws Exception {
    when(authService.callbackFailure("google", "expected-state"))
        .thenThrow(new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("error", "provider_error")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verify(authService).callbackFailure("google", "expected-state");
    verify(authService, never()).callback("google", null, "expected-state");
  }

  @Test
  void redirectsAnyCustomFailureDuringFailureCallback() throws Exception {
    when(authService.callbackFailure("google", "expected-state"))
        .thenThrow(new CustomException(GlobalErrorCode.UNAUTHORIZED));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("error", "provider_error")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));
  }

  @Test
  void redirectsUnexpectedFailureDuringFailureCallback() throws Exception {
    when(authService.callbackFailure("google", "expected-state"))
        .thenThrow(new IllegalStateException("raw failure"));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("error", "provider_error")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));
  }

  @Test
  void redirectsProviderExchangeFailureWithoutLeakingRawCallbackValues() throws Exception {
    when(authService.callback("google", "authorization-code", "expected-state"))
        .thenThrow(new AuthService.CallbackExchangeFailure("/dashboard"));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .param("error_description", "raw provider detail")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION, "/login?authError=oauth_failed&returnTo=%2Fdashboard"))
        .andExpect(
            result -> {
              assertThat(result.getResponse().getContentAsString())
                  .doesNotContain("authorization-code", "expected-state", "raw provider detail");
              assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                  .noneMatch(cookie -> cookie.contains(RefreshTokenCookie.NAME));
              assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                  .anyMatch(cookie -> cookie.contains(STATE_COOKIE + "=;"));
            });

    verify(authService).callback("google", "authorization-code", "expected-state");
  }

  @Test
  void redirectsAnyCustomFailureDuringSuccessfulCallback() throws Exception {
    when(authService.callback("google", "authorization-code", "expected-state"))
        .thenThrow(new CustomException(GlobalErrorCode.UNAUTHORIZED));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));
  }

  @Test
  void redirectsUnexpectedFailureDuringSuccessfulCallback() throws Exception {
    when(authService.callback("google", "authorization-code", "expected-state"))
        .thenThrow(new IllegalStateException("raw failure"));

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(header().string(HttpHeaders.LOCATION, "/login?authError=oauth_failed"))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));
  }

  @Test
  void completesCallbackOnlyForMatchingBrowserStateAndClearsIt() throws Exception {
    when(authService.callback("google", "authorization-code", "expected-state"))
        .thenReturn(new AuthService.CallbackResult("/", "refresh-token"));
    when(properties.refreshTokenDays()).thenReturn(14);

    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state")
                .cookie(new Cookie(STATE_COOKIE, "expected-state")))
        .andExpect(status().isFound())
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE,
                    hasItem(
                        allOf(
                            containsString(RefreshTokenCookie.NAME),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("Path=/"),
                            containsString("SameSite=Lax")))))
        .andExpect(
            header()
                .stringValues(
                    HttpHeaders.SET_COOKIE, hasItem(containsString(STATE_COOKIE + "=;"))));

    verify(authService).callback("google", "authorization-code", "expected-state");
  }

  @Test
  void refreshReturnsAccessToken() throws Exception {
    when(refreshTokenService.refresh("refresh-token")).thenReturn("access-token");

    mockMvc
        .perform(
            post("/api/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, "refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").value("access-token"));
  }

  @Test
  void currentUserReturnsProfileImageUrl() throws Exception {
    Member member =
        Member.create(
            OAuthProvider.GOOGLE,
            "oauth-id",
            "member@example.com",
            "Readler",
            "https://example.com/profile.png");
    ReflectionTestUtils.setField(member, "uuid", "member-uuid");
    when(authService.currentMember("member-uuid")).thenReturn(member);
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            "member-uuid", null, AuthorityUtils.createAuthorityList("ROLE_USER"));

    mockMvc
        .perform(
            get("/api/users/me").with(authentication(authentication)).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/profile.png"));
  }

  @Test
  void currentUserRejectsMissingIdentity() {
    AuthController controller = new AuthController(authService, refreshTokenService, properties);

    assertThatThrownBy(() -> controller.currentUser(null))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
  }

  @Test
  void currentUserRejectsAnonymousIdentity() {
    Authentication authentication =
        new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    AuthController controller = new AuthController(authService, refreshTokenService, properties);

    assertThatThrownBy(() -> controller.currentUser(authentication))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
  }

  @Test
  void currentUserRejectsUnauthenticatedIdentity() {
    Authentication authentication = new UsernamePasswordAuthenticationToken("member-uuid", null);
    AuthController controller = new AuthController(authService, refreshTokenService, properties);

    assertThatThrownBy(() -> controller.currentUser(authentication))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
  }

  @Test
  void sessionReportsUnauthenticatedNonAnonymousIdentityAsUnauthenticated() {
    Authentication authentication = new UsernamePasswordAuthenticationToken("member-uuid", null);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        CsrfToken.class.getName(), new DefaultCsrfToken("X-XSRF-TOKEN", "XSRF-TOKEN", "token"));
    AuthController controller = new AuthController(authService, refreshTokenService, properties);

    AuthController.ApiResponse<AuthController.SessionResponse> response =
        controller.session(authentication, request, "refresh-token");

    assertThat(response.data().authenticated()).isFalse();
    assertThat(response.data().uuid()).isNull();
    verify(refreshTokenService, never()).isActiveForMember("refresh-token", "member-uuid");
  }

  @Test
  void logoutForwardsAuthenticatedPrincipalToRefreshTokenService() {
    new AuthController(authService, refreshTokenService, properties)
        .logout(
            new UsernamePasswordAuthenticationToken(
                "member-uuid", null, AuthorityUtils.createAuthorityList("ROLE_USER")),
            "refresh-token");

    verify(refreshTokenService).revoke("refresh-token", "member-uuid");
  }

  @Test
  void logoutRevokesRefreshTokenWithoutUnauthenticatedPrincipal() {
    new AuthController(authService, refreshTokenService, properties)
        .logout(new UsernamePasswordAuthenticationToken("member-uuid", null), "refresh-token");

    verify(refreshTokenService).revoke("refresh-token", null);
  }

  @Test
  void logoutRevokesRefreshTokenWithoutPrincipalAndDeletesCookie() {
    var response =
        new AuthController(authService, refreshTokenService, properties)
            .logout(null, "refresh-token");

    verify(refreshTokenService).revoke("refresh-token", null);
    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
        .anySatisfy(
            cookie ->
                assertThat(cookie)
                    .contains(RefreshTokenCookie.NAME)
                    .contains("Max-Age=0")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("Path=/")
                    .contains("SameSite=Lax"));
  }
}
