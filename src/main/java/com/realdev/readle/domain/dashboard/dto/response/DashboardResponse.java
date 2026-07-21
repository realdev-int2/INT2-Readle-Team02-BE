package com.realdev.readle.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardResponse {
  private final Totals totals;
  private final List<TagSummary> tagSummaries;
  private final List<RecentRecord> recentRecords;

  @Getter
  @Builder
  public static class Totals {
    private final Integer completedQuizCount;
    private final Integer totalQuestionCount;
    private final Integer tagCount;
    private final BigDecimal averageAccuracyRate;
    private final LocalDateTime lastCompletedAt;
  }

  @Getter
  @Builder
  public static class TagInfo {
    private final Long tagId;
    private final String name;
  }

  @Getter
  @Builder
  public static class TagSummary {
    private final Long tagId;
    private final String name;
    private final Integer completedCount;
    private final BigDecimal averageAccuracyRate;
  }

  @Getter
  @Builder
  public static class RecentRecord {
    private final Long reportId;
    private final Long quizId;
    private final String title;
    private final BigDecimal accuracyRate;
    private final Integer correctCount;
    private final Integer totalCount;
    private final LocalDateTime completedAt;
    private final List<TagInfo> tags;
  }
}
