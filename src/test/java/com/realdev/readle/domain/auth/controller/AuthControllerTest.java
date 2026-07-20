package com.realdev.readle.domain.auth.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.auth.RefreshTokenCookie;
import com.realdev.readle.domain.auth.service.AuthService;
import com.realdev.readle.domain.auth.service.RefreshTokenService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
        .andExpect(status().isBadRequest());

    verify(authService, never()).callback("google", "authorization-code", "expected-state");
  }

  @Test
  void rejectsCallbackWithoutBrowserStateBeforeTokenIssuance() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/google/callback")
                .param("code", "authorization-code")
                .param("state", "expected-state"))
        .andExpect(status().isBadRequest());

    verify(authService, never()).callback("google", "authorization-code", "expected-state");
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
  void logoutForwardsAuthenticatedPrincipalToRefreshTokenService() {
    new AuthController(authService, refreshTokenService, properties)
        .logout(new UsernamePasswordAuthenticationToken("member-uuid", null), "refresh-token");

    verify(refreshTokenService).revoke("refresh-token", "member-uuid");
  }
}
