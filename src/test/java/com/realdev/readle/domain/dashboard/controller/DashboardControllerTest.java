package com.realdev.readle.domain.dashboard.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import com.realdev.readle.domain.dashboard.service.DashboardService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DashboardService dashboardService;

  private Authentication createMockAuthentication() {
    return new UsernamePasswordAuthenticationToken(
        "test-uuid-1234", "N/A", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  @DisplayName("GET /api/dashboard - 인증된 사용자가 대시보드 조회 시 200 OK 및 요약 정보를 반환한다")
  void getDashboard_Success() throws Exception {
    Authentication auth = createMockAuthentication();

    DashboardResponse.Totals mockTotals =
        DashboardResponse.Totals.builder()
            .completedQuizCount(12)
            .totalQuestionCount(58)
            .tagCount(9)
            .averageAccuracyRate(new BigDecimal("78.40"))
            .lastCompletedAt(LocalDateTime.of(2026, 7, 16, 11, 48))
            .build();

    DashboardResponse.TagSummary mockTag =
        DashboardResponse.TagSummary.builder()
            .tagId(1L)
            .name("spring")
            .completedCount(4)
            .averageAccuracyRate(new BigDecimal("86.00"))
            .build();

    DashboardResponse.RecentRecord mockRecord =
        DashboardResponse.RecentRecord.builder()
            .reportId(601L)
            .quizId(201L)
            .title("Spring @Transactional 심층 이해")
            .accuracyRate(new BigDecimal("60.00"))
            .correctCount(3)
            .totalCount(5)
            .completedAt(LocalDateTime.of(2026, 7, 16, 11, 48))
            .tags(
                List.of(
                    DashboardResponse.TagInfo.builder().tagId(1L).name("spring").build(),
                    DashboardResponse.TagInfo.builder().tagId(2L).name("transaction").build()))
            .build();

    DashboardResponse mockResponse =
        DashboardResponse.builder()
            .totals(mockTotals)
            .tagSummaries(List.of(mockTag))
            .recentRecords(List.of(mockRecord))
            .build();

    when(dashboardService.getDashboardSummary(anyString())).thenReturn(mockResponse);

    mockMvc
        .perform(get("/api/dashboard").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.completedQuizCount").value(12))
        .andExpect(jsonPath("$.totals.totalQuestionCount").value(58))
        .andExpect(jsonPath("$.totals.tagCount").value(9))
        .andExpect(jsonPath("$.tagSummaries[0].name").value("spring"))
        .andExpect(jsonPath("$.recentRecords[0].title").value("Spring @Transactional 심층 이해"))
        .andExpect(jsonPath("$.recentRecords[0].tags[0].tagId").value(1))
        .andExpect(jsonPath("$.recentRecords[0].tags[0].name").value("spring"));
  }

  @Test
  @DisplayName("GET /api/dashboard - 인증되지 않은 요청은 401 Unauthorized를 반환한다")
  void getDashboard_Unauthorized() throws Exception {
    mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());

    verifyNoInteractions(dashboardService);
  }
}
