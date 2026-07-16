package com.realdev.readle.domain.quiz.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.quiz.dto.response.QuizAttemptResultResponse;
import com.realdev.readle.domain.quiz.entity.AttemptStatus;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.exception.QuizErrorCode;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizSolveController.class)
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
class QuizSolveControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private QuizSolveService quizSolveService;
  @MockitoBean private JwtService jwtService;

  @Test
  @DisplayName(
      "인증된 사용자가 퀴즈 풀이를 시작하면 200 OK와 스펙에 규정된 quizId, status(in_progress), startedAt, attemptId를 반환한다")
  void startQuiz_Success() throws Exception {
    // given
    UUID memberUuid = UUID.randomUUID();
    Authentication auth =
        new UsernamePasswordAuthenticationToken(memberUuid.toString(), null, List.of());

    QuizSet mockQuizSet = Mockito.mock(QuizSet.class);
    when(mockQuizSet.getId()).thenReturn(201L);

    Member mockMember = Mockito.mock(Member.class);
    when(mockMember.getUuid()).thenReturn(memberUuid.toString());

    QuizAttempt mockAttempt = Mockito.mock(QuizAttempt.class);
    when(mockAttempt.getId()).thenReturn(601L);
    when(mockAttempt.getQuizSet()).thenReturn(mockQuizSet);
    when(mockAttempt.getMember()).thenReturn(mockMember);
    when(mockAttempt.getStatus()).thenReturn(AttemptStatus.IN_PROGRESS);
    when(mockAttempt.getStartedAt()).thenReturn(LocalDateTime.of(2026, 7, 10, 8, 30, 0));

    when(quizSolveService.startQuiz(anyLong(), anyString())).thenReturn(mockAttempt);

    // when & then
    mockMvc
        .perform(
            post("/api/quizzes/201/attempts")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attemptId").value(601))
        .andExpect(jsonPath("$.quizId").value(201))
        .andExpect(jsonPath("$.status").value("in_progress"))
        .andExpect(jsonPath("$.startedAt").value("2026-07-10T08:30:00"));
  }

  @Test
  @DisplayName("GET /api/quizzes/attempts/{attemptId}/result - 학습 이력 상세 조회 성공 시 200 OK와 DTO를 반환한다")
  void getAttemptResult_Success() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth =
        new UsernamePasswordAuthenticationToken(memberUuid.toString(), null, List.of());

    QuizAttemptResultResponse.QuestionResult qResult =
        QuizAttemptResultResponse.QuestionResult.builder()
            .questionId(10L)
            .submittedAnswer("test answer")
            .isCorrect(true)
            .aiFeedback("good")
            .build();

    QuizAttemptResultResponse mockResponse =
        QuizAttemptResultResponse.builder()
            .accuracyRate(new BigDecimal("100.00"))
            .correctCount(1)
            .totalCount(1)
            .solveDurationSeconds(60)
            .completedAt(LocalDateTime.of(2026, 7, 10, 8, 31, 0))
            .results(List.of(qResult))
            .build();

    when(quizSolveService.getAttemptResult(anyString(), anyLong())).thenReturn(mockResponse);

    mockMvc
        .perform(get("/api/quizzes/attempts/601/result").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accuracyRate").value(100.0))
        .andExpect(jsonPath("$.correctCount").value(1))
        .andExpect(jsonPath("$.results[0].questionId").value(10))
        .andExpect(jsonPath("$.results[0].submittedAnswer").value("test answer"));
  }

  @Test
  @DisplayName("GET /api/quizzes/attempts/{attemptId}/result - 존재하지 않는 시도면 404 NOT_FOUND를 반환한다")
  void getAttemptResult_NotFound() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid.toString(), null, List.of());

    when(quizSolveService.getAttemptResult(anyString(), anyLong()))
        .thenThrow(new CustomException(QuizErrorCode.ATTEMPT_NOT_FOUND));

    mockMvc
        .perform(get("/api/quizzes/attempts/601/result").with(authentication(auth)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /api/quizzes/attempts/{attemptId}/result - 타인의 시도를 조회하면 403 FORBIDDEN을 반환한다")
  void getAttemptResult_Forbidden() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid.toString(), null, List.of());

    when(quizSolveService.getAttemptResult(anyString(), anyLong()))
        .thenThrow(new CustomException(QuizErrorCode.FORBIDDEN_ACCESS));

    mockMvc
        .perform(get("/api/quizzes/attempts/601/result").with(authentication(auth)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /api/quizzes/attempts/{attemptId}/result - 미제출 상태에서 조회 시 400 BAD_REQUEST를 반환한다")
  void getAttemptResult_NotSubmitted() throws Exception {
    UUID memberUuid = UUID.randomUUID();
    Authentication auth = new UsernamePasswordAuthenticationToken(memberUuid.toString(), null, List.of());

    when(quizSolveService.getAttemptResult(anyString(), anyLong()))
        .thenThrow(new CustomException(QuizErrorCode.ATTEMPT_NOT_SUBMITTED));

    mockMvc
        .perform(get("/api/quizzes/attempts/601/result").with(authentication(auth)))
        .andExpect(status().isBadRequest());
  }
}
