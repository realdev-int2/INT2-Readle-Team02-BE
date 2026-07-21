package com.realdev.readle.domain.quiz.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResultReportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
    properties = {
      "security.jwtIssuer=test-issuer",
      "security.jwtSecret=test-secret-test-secret-test-secret-32bytes",
      "security.jwtAudience=test-audience",
      "security.accessTokenMinutes=30",
      "security.refreshTokenDays=7",
      "security.stateEncryptionKey=MDEyMzQ1Njc4OWFiY2RlZmdoaWprbG1ub3BxcnN0dXY=", // gitleaks:allow
      "security.stateMinutes=5",
      "security.backendOrigin=http://localhost:8080"
    })
class ResultReportControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QuizSolveService quizSolveService;
  @MockitoBean private JwtService jwtService;

  @Test
  @DisplayName("인증된 사용자의 UUID로 결과 리포트 상세를 조회한다")
  void getResultReport_UsesAuthenticatedMemberUuid() throws Exception {
    String memberUuid = "member-uuid";
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    QuizAttemptResultResponse response =
        QuizAttemptResultResponse.builder()
            .quizSetId(201L)
            .attemptId(601L)
            .title("Spring 학습 결과")
            .tags(List.of("spring"))
            .accuracyRate(new BigDecimal("80.00"))
            .correctCount(4)
            .totalCount(5)
            .solveDurationSeconds(120)
            .completedAt(LocalDateTime.of(2026, 7, 21, 10, 0))
            .results(List.of())
            .build();
    given(quizSolveService.getResultReport(memberUuid, 701L)).willReturn(response);

    mockMvc
        .perform(get("/api/result-reports/701").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quizSetId").value(201))
        .andExpect(jsonPath("$.attemptId").value(601))
        .andExpect(jsonPath("$.title").value("Spring 학습 결과"));

    then(quizSolveService).should().getResultReport(memberUuid, 701L);
  }

  @Test
  @DisplayName("존재하지 않는 결과 리포트를 조회하면 404를 반환한다")
  void getResultReport_NotFound() throws Exception {
    String memberUuid = "member-uuid";
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    given(quizSolveService.getResultReport(memberUuid, 701L))
        .willThrow(new CustomException(QuizErrorCode.RESULT_REPORT_NOT_FOUND));

    mockMvc
        .perform(get("/api/result-reports/701").with(authentication(authentication)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("RESULT_REPORT_NOT_FOUND"));
  }

  @Test
  @DisplayName("다른 사용자의 결과 리포트를 조회하면 403을 반환한다")
  void getResultReport_Forbidden() throws Exception {
    String memberUuid = "member-uuid";
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(memberUuid, null, List.of());
    given(quizSolveService.getResultReport(memberUuid, 701L))
        .willThrow(new CustomException(QuizErrorCode.FORBIDDEN_ACCESS));

    mockMvc
        .perform(get("/api/result-reports/701").with(authentication(authentication)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN_ACCESS"));
  }
}
