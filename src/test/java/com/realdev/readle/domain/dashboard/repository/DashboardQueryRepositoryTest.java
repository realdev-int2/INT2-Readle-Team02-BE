package com.realdev.readle.domain.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardQueryRepositoryTest {

  private static final String MEMBER_UUID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_MEMBER_UUID = "22222222-2222-2222-2222-222222222222";

  @Autowired private DashboardQueryRepository dashboardQueryRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void setUp() {
    insertMember(1L, MEMBER_UUID, "member-1");
    insertMember(2L, OTHER_MEMBER_UUID, "member-2");

    insertLearningRecord(
        101L, 201L, 301L, 401L, 701L, 1L, "HTTP 기초", 1, 2, LocalDateTime.of(2026, 7, 18, 10, 0));
    insertLearningRecord(
        102L,
        202L,
        302L,
        402L,
        702L,
        1L,
        "Spring 트랜잭션",
        4,
        5,
        LocalDateTime.of(2026, 7, 20, 11, 30));
    insertLearningRecord(
        103L, 203L, 303L, 403L, 703L, 2L, "다른 사용자의 학습", 5, 5, LocalDateTime.of(2026, 7, 20, 12, 0));

    insertTag(801L, "http");
    insertTag(802L, "spring");
    insertContentTag(901L, 101L, 801L);
    insertContentTag(902L, 101L, 802L);
    insertContentTag(903L, 102L, 802L);
  }

  @Test
  @DisplayName("전체 현황은 현재 사용자의 제출 완료 결과만 문제 수 기준으로 가중 집계한다")
  void fetchTotals_AggregatesOnlyCurrentMemberSubmittedResults() {
    DashboardResponse.Totals totals = dashboardQueryRepository.fetchTotals(MEMBER_UUID);

    assertThat(totals.getCompletedQuizCount()).isEqualTo(2);
    assertThat(totals.getTotalQuestionCount()).isEqualTo(7);
    assertThat(totals.getTagCount()).isEqualTo(2);
    assertThat(totals.getAverageAccuracyRate()).isEqualByComparingTo("71.43");
    assertThat(totals.getLastCompletedAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 11, 30));
  }

  @Test
  @DisplayName("태그 현황은 학습 횟수 순으로 집계하고 동일 태그의 정답률을 가중 계산한다")
  void fetchTagSummaries_AggregatesByTag() {
    List<DashboardResponse.TagSummary> summaries =
        dashboardQueryRepository.fetchTagSummaries(MEMBER_UUID);

    assertThat(summaries).hasSize(2);
    assertThat(summaries.get(0).getName()).isEqualTo("spring");
    assertThat(summaries.get(0).getCompletedCount()).isEqualTo(2);
    assertThat(summaries.get(0).getAverageAccuracyRate()).isEqualByComparingTo("71.43");
    assertThat(summaries.get(1).getName()).isEqualTo("http");
    assertThat(summaries.get(1).getCompletedCount()).isEqualTo(1);
    assertThat(summaries.get(1).getAverageAccuracyRate()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("최근 학습 이력은 결과 ID와 태그 객체를 반환하며 태그 조회 N+1이 발생하지 않는다")
  void fetchRecentRecords_ReturnsReportIdAndTagsWithoutNPlusOne() {
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    List<DashboardResponse.RecentRecord> records =
        dashboardQueryRepository.fetchRecentRecords(MEMBER_UUID, 5);

    assertThat(records).hasSize(2);
    assertThat(records.get(0).getReportId()).isEqualTo(702L);
    assertThat(records.get(0).getQuizId()).isEqualTo(302L);
    assertThat(records.get(0).getTitle()).isEqualTo("Spring 트랜잭션");
    assertThat(records.get(0).getTags())
        .extracting(DashboardResponse.TagInfo::getTagId, DashboardResponse.TagInfo::getName)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(802L, "spring"));
    assertThat(records.get(1).getReportId()).isEqualTo(701L);
    assertThat(records.get(1).getTags())
        .extracting(DashboardResponse.TagInfo::getName)
        .containsExactly("http", "spring");
    assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("학습 이력이 없는 사용자는 빈 대시보드 값을 반환한다")
  void fetchDashboardData_ReturnsEmptyValues() {
    DashboardResponse.Totals totals = dashboardQueryRepository.fetchTotals("unknown-member");

    assertThat(totals.getCompletedQuizCount()).isZero();
    assertThat(totals.getTotalQuestionCount()).isZero();
    assertThat(totals.getTagCount()).isZero();
    assertThat(totals.getAverageAccuracyRate()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(totals.getLastCompletedAt()).isNull();
    assertThat(dashboardQueryRepository.fetchTagSummaries("unknown-member")).isEmpty();
    assertThat(dashboardQueryRepository.fetchRecentRecords("unknown-member", 5)).isEmpty();
  }

  private void insertMember(Long id, String uuid, String oauthId) {
    jdbcTemplate.update(
        """
        INSERT INTO member
          (id, uuid, oauth_provider, oauth_id, nickname, created_at, updated_at, last_login_at)
        VALUES (?, ?, 'GOOGLE', ?, ?, ?, ?, ?)
        """,
        id,
        uuid,
        oauthId,
        oauthId,
        LocalDateTime.of(2026, 7, 1, 0, 0),
        LocalDateTime.of(2026, 7, 1, 0, 0),
        LocalDateTime.of(2026, 7, 1, 0, 0));
  }

  private void insertLearningRecord(
      Long contentId,
      Long validationId,
      Long quizSetId,
      Long attemptId,
      Long resultId,
      Long memberId,
      String title,
      int correctCount,
      int totalCount,
      LocalDateTime completedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO content
          (id, member_id, title, input_type, raw_text, crawl_status, created_at, updated_at)
        VALUES (?, ?, ?, 'TEXT', '테스트 본문', 'NOT_APPLICABLE', ?, ?)
        """,
        contentId,
        memberId,
        title,
        completedAt.minusHours(2),
        completedAt.minusHours(2));
    jdbcTemplate.update(
        """
        INSERT INTO content_validation
          (id, content_id, validation_method, status, validation_score, created_at, validated_at)
        VALUES (?, ?, 'AI', 'PASSED', 90.00, ?, ?)
        """,
        validationId,
        contentId,
        completedAt.minusHours(2),
        completedAt.minusHours(1));
    jdbcTemplate.update(
        """
        INSERT INTO quiz_set
          (id, content_id, status, question_count, created_at, completed_at,
           is_bypassed, source_validation_id)
        VALUES (?, ?, 'COMPLETED', ?, ?, ?, FALSE, ?)
        """,
        quizSetId,
        contentId,
        totalCount,
        completedAt.minusHours(1),
        completedAt.minusMinutes(50),
        validationId);
    jdbcTemplate.update(
        """
        INSERT INTO quiz_attempt
          (id, quiz_set_id, member_id, status, started_at, submitted_at)
        VALUES (?, ?, ?, 'SUBMITTED', ?, ?)
        """,
        attemptId,
        quizSetId,
        memberId,
        completedAt.minusMinutes(10),
        completedAt);
    jdbcTemplate.update(
        """
        INSERT INTO quiz_result
          (id, attempt_id, accuracy_rate, correct_count, total_count,
           solve_duration_seconds, completed_at)
        VALUES (?, ?, ?, ?, ?, 600, ?)
        """,
        resultId,
        attemptId,
        BigDecimal.valueOf(correctCount)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP),
        correctCount,
        totalCount,
        completedAt);
  }

  private void insertTag(Long id, String name) {
    jdbcTemplate.update(
        "INSERT INTO tag (id, name, created_at) VALUES (?, ?, ?)",
        id,
        name,
        LocalDateTime.of(2026, 7, 1, 0, 0));
  }

  private void insertContentTag(Long id, Long contentId, Long tagId) {
    jdbcTemplate.update(
        "INSERT INTO content_tag (id, content_id, tag_id, created_at) VALUES (?, ?, ?, ?)",
        id,
        contentId,
        tagId,
        LocalDateTime.of(2026, 7, 1, 0, 0));
  }
}
