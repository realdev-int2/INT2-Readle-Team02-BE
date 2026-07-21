package com.realdev.readle.domain.dashboard.service;

import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import com.realdev.readle.domain.dashboard.repository.DashboardQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

  private final DashboardQueryRepository dashboardQueryRepository;

  public DashboardResponse getDashboardSummary(String memberUuid) {
    List<DashboardResponse.TagSummary> tagSummaries =
        dashboardQueryRepository.fetchTagSummaries(memberUuid);
    DashboardResponse.Totals totals =
        dashboardQueryRepository.fetchTotals(memberUuid, tagSummaries.size());
    List<DashboardResponse.RecentRecord> recentRecords =
        dashboardQueryRepository.fetchRecentRecords(memberUuid, 5);

    return DashboardResponse.builder()
        .totals(totals)
        .tagSummaries(tagSummaries)
        .recentRecords(recentRecords)
        .build();
  }
}
