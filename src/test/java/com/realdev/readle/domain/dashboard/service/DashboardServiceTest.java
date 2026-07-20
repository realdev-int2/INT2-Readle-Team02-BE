package com.realdev.readle.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import com.realdev.readle.domain.dashboard.repository.DashboardQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private DashboardQueryRepository dashboardQueryRepository;

  @InjectMocks private DashboardService dashboardService;

  @Test
  @DisplayName("getDashboardSummary 성공 - 사용자의 학습 현황 통계와 태그 요약 및 최근 이력을 조합한다")
  void getDashboardSummary_Success() {
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

    given(dashboardQueryRepository.fetchTotals("test-uuid")).willReturn(mockTotals);
    given(dashboardQueryRepository.fetchTagSummaries("test-uuid")).willReturn(List.of(mockTag));
    given(dashboardQueryRepository.fetchRecentRecords("test-uuid", 5))
        .willReturn(List.of(mockRecord));

    DashboardResponse response = dashboardService.getDashboardSummary("test-uuid");

    assertThat(response).isNotNull();
    assertThat(response.getTotals().getCompletedQuizCount()).isEqualTo(12);
    assertThat(response.getTotals().getTotalQuestionCount()).isEqualTo(58);
    assertThat(response.getTotals().getTagCount()).isEqualTo(9);
    assertThat(response.getTagSummaries()).hasSize(1);
    assertThat(response.getTagSummaries().get(0).getName()).isEqualTo("spring");
    assertThat(response.getRecentRecords()).hasSize(1);
    assertThat(response.getRecentRecords().get(0).getTitle())
        .isEqualTo("Spring @Transactional 심층 이해");
  }
}
