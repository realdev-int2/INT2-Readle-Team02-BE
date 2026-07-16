package com.realdev.readle.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.ReadleApplication;
import com.realdev.readle.domain.auth.RefreshTokenCookie;
import com.realdev.readle.domain.auth.service.RefreshTokenService;
import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = ReadleApplication.class,
    properties = {
      "management.endpoints.web.base-path=/api/actuator",
      "management.endpoint.health.probes.enabled=true"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityBoundaryTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private MemberRepository memberRepository;

  @Autowired private RefreshTokenService refreshTokenService;

  @Autowired private JwtService jwtService;

  @Test
  void deniesUnlistedRoutesWithJsonUnauthorizedResponse() throws Exception {
    mockMvc
        .perform(get("/api/private"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.details").isArray());
  }

  @Test
  void returnsStandardUnauthorizedJsonForMalformedBearerToken() throws Exception {
    mockMvc
        .perform(get("/api/auth/session").header("Authorization", "Bearer malformed-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.details").isArray());
  }

  @Test
  void reportsAnonymousSessionsAsUnauthenticated() throws Exception {
    mockMvc
        .perform(get("/api/auth/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(false))
        .andExpect(jsonPath("$.data.uuid").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void reportsJwtOnlySessionsAsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/session")
                .header("Authorization", "Bearer " + jwtService.issue("member-uuid")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(false))
        .andExpect(jsonPath("$.data.uuid").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void reportsJwtSessionsWithUnknownRefreshTokenAsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/auth/session")
                .header("Authorization", "Bearer " + jwtService.issue("member-uuid"))
                .cookie(new Cookie(RefreshTokenCookie.NAME, "unknown-refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(false))
        .andExpect(jsonPath("$.data.uuid").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void reportsSessionAsAuthenticatedWhenJwtAndRefreshTokenBelongToSameMember() throws Exception {
    Member member =
        memberRepository.save(
            Member.create(OAuthProvider.GOOGLE, "session-active", null, "tester", null));
    String refreshToken = refreshTokenService.issue(member);

    mockMvc
        .perform(
            get("/api/auth/session")
                .header("Authorization", "Bearer " + jwtService.issue(member.getUuid()))
                .cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(true))
        .andExpect(jsonPath("$.data.uuid").value(member.getUuid()));
  }

  @Test
  void reportsSessionAsUnauthenticatedWhenRefreshTokenBelongsToAnotherMember() throws Exception {
    Member jwtMember =
        memberRepository.save(
            Member.create(OAuthProvider.GOOGLE, "session-jwt-member", null, "tester", null));
    Member refreshMember =
        memberRepository.save(
            Member.create(OAuthProvider.GOOGLE, "session-refresh-member", null, "tester", null));
    String refreshToken = refreshTokenService.issue(refreshMember);

    mockMvc
        .perform(
            get("/api/auth/session")
                .header("Authorization", "Bearer " + jwtService.issue(jwtMember.getUuid()))
                .cookie(new Cookie(RefreshTokenCookie.NAME, refreshToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.authenticated").value(false))
        .andExpect(jsonPath("$.data.uuid").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void permitsPrefixedActuatorHealthEndpoint() throws Exception {
    mockMvc.perform(get("/api/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void permitsPrefixedActuatorHealthReadinessEndpoint() throws Exception {
    mockMvc.perform(get("/api/actuator/health/readiness")).andExpect(status().isOk());
  }

  @Test
  void deniesFallbackRoutesWithJsonUnauthorizedResponse() throws Exception {
    mockMvc
        .perform(get("/private"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.error.details").isArray());
  }

  @Test
  void allowsBearerExtractRequestWithoutCsrfToReachControllerValidation() throws Exception {
    mockMvc
        .perform(
            post("/api/contents/extract")
                .header("Authorization", "Bearer " + jwtService.issue("member-uuid"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
  }

  @Test
  void issuesClientReadableCsrfTokenAndRequiresItForRefreshAndLogout() throws Exception {
    String token =
        mockMvc
            .perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
            .andReturn()
            .getResponse()
            .getCookie("XSRF-TOKEN")
            .getValue();

    mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/auth/refresh")
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isUnauthorized());

    Member member =
        memberRepository.save(
            Member.create(OAuthProvider.GOOGLE, "security-boundary", null, "tester", null));
    String refreshToken = refreshTokenService.issue(member);

    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(
                    new Cookie("XSRF-TOKEN", token),
                    new Cookie(RefreshTokenCookie.NAME, refreshToken))
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token)
                .header("Authorization", "Bearer " + jwtService.issue("member-uuid")))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString(RefreshTokenCookie.NAME))));
  }
}
