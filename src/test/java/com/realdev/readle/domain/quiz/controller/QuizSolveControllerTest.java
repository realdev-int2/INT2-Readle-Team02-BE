package com.realdev.readle.domain.quiz.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.quiz.entity.AttemptStatus;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizSet;
import com.realdev.readle.domain.quiz.service.QuizSolveService;
import com.realdev.readle.global.security.JwtService;
import com.realdev.readle.global.security.SecurityConfig;
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
}
