package com.realdev.readle.domain.dashboard.repository;

import static com.realdev.readle.domain.content.entity.QContent.content;
import static com.realdev.readle.domain.quiz.entity.QQuizAttempt.quizAttempt;
import static com.realdev.readle.domain.quiz.entity.QQuizResult.quizResult;
import static com.realdev.readle.domain.quiz.entity.QQuizSet.quizSet;
import static com.realdev.readle.domain.tag.entity.QContentTag.contentTag;
import static com.realdev.readle.domain.tag.entity.QTag.tag;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.realdev.readle.domain.dashboard.dto.response.DashboardResponse;
import com.realdev.readle.domain.quiz.entity.AttemptStatus;
import com.realdev.readle.domain.quiz.entity.QuizAttempt;
import com.realdev.readle.domain.quiz.entity.QuizResult;
import com.realdev.readle.domain.tag.repository.ContentTagRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

  private final JPAQueryFactory queryFactory;
  private final ContentTagRepository contentTagRepository;

  public DashboardResponse.Totals fetchTotals(String memberUuid) {
    Tuple aggregate =
        queryFactory
            .select(
                quizResult.count(),
                quizResult.correctCount.sum(),
                quizResult.totalCount.sum(),
                quizResult.completedAt.max())
            .from(quizResult)
            .join(quizResult.quizAttempt, quizAttempt)
            .where(
                quizAttempt.member.uuid.eq(memberUuid),
                quizAttempt.status.eq(AttemptStatus.SUBMITTED))
            .fetchOne();

    Long completedQuizCount = aggregate != null ? aggregate.get(0, Long.class) : null;
    Number correctCountSum = aggregate != null ? aggregate.get(1, Number.class) : null;
    Number totalQuestionCountSum = aggregate != null ? aggregate.get(2, Number.class) : null;
    Integer correctCount = correctCountSum != null ? correctCountSum.intValue() : null;
    Integer totalQuestionCount =
        totalQuestionCountSum != null ? totalQuestionCountSum.intValue() : null;
    LocalDateTime lastCompletedAt =
        aggregate != null ? aggregate.get(3, LocalDateTime.class) : null;

    Long learnedTagCount =
        queryFactory
            .select(contentTag.tag.id.countDistinct())
            .from(quizResult)
            .join(quizResult.quizAttempt, quizAttempt)
            .join(quizAttempt.quizSet, quizSet)
            .join(quizSet.content, content)
            .join(contentTag)
            .on(contentTag.content.id.eq(content.id))
            .where(
                quizAttempt.member.uuid.eq(memberUuid),
                quizAttempt.status.eq(AttemptStatus.SUBMITTED))
            .fetchOne();

    return DashboardResponse.Totals.builder()
        .completedQuizCount(completedQuizCount != null ? completedQuizCount.intValue() : 0)
        .totalQuestionCount(totalQuestionCount != null ? totalQuestionCount : 0)
        .tagCount(learnedTagCount != null ? learnedTagCount.intValue() : 0)
        .averageAccuracyRate(calculateAccuracyRate(correctCount, totalQuestionCount))
        .lastCompletedAt(lastCompletedAt)
        .build();
  }

  public List<DashboardResponse.TagSummary> fetchTagSummaries(String memberUuid) {
    List<Tuple> tuples =
        queryFactory
            .select(
                tag.id,
                tag.name,
                quizResult.count(),
                quizResult.correctCount.sum(),
                quizResult.totalCount.sum())
            .from(quizResult)
            .join(quizResult.quizAttempt, quizAttempt)
            .join(quizAttempt.quizSet, quizSet)
            .join(quizSet.content, content)
            .join(contentTag)
            .on(contentTag.content.id.eq(content.id))
            .join(contentTag.tag, tag)
            .where(
                quizAttempt.member.uuid.eq(memberUuid),
                quizAttempt.status.eq(AttemptStatus.SUBMITTED))
            .groupBy(tag.id, tag.name)
            .orderBy(quizResult.count().desc(), tag.name.asc())
            .fetch();

    List<DashboardResponse.TagSummary> summaries = new ArrayList<>();
    for (Tuple tuple : tuples) {
      Long tagId = tuple.get(0, Long.class);
      String tagName = tuple.get(1, String.class);
      Long count = tuple.get(2, Long.class);
      Number correctCountSum = tuple.get(3, Number.class);
      Number totalCountSum = tuple.get(4, Number.class);
      Integer correctCount = correctCountSum != null ? correctCountSum.intValue() : null;
      Integer totalCount = totalCountSum != null ? totalCountSum.intValue() : null;

      summaries.add(
          DashboardResponse.TagSummary.builder()
              .tagId(tagId)
              .name(tagName)
              .completedCount(count != null ? count.intValue() : 0)
              .averageAccuracyRate(calculateAccuracyRate(correctCount, totalCount))
              .build());
    }
    return summaries;
  }

  public List<DashboardResponse.RecentRecord> fetchRecentRecords(String memberUuid, int limit) {
    List<QuizResult> results =
        queryFactory
            .selectFrom(quizResult)
            .join(quizResult.quizAttempt, quizAttempt)
            .fetchJoin()
            .join(quizAttempt.quizSet, quizSet)
            .fetchJoin()
            .join(quizSet.content, content)
            .fetchJoin()
            .where(
                quizAttempt.member.uuid.eq(memberUuid),
                quizAttempt.status.eq(AttemptStatus.SUBMITTED))
            .orderBy(quizResult.completedAt.desc(), quizResult.id.desc())
            .limit(limit)
            .fetch();

    List<Long> contentIds =
        results.stream()
            .map(result -> result.getQuizAttempt().getQuizSet().getContent().getId())
            .distinct()
            .toList();
    Map<Long, List<DashboardResponse.TagInfo>> tagsByContentId = fetchTagsByContentId(contentIds);

    List<DashboardResponse.RecentRecord> records = new ArrayList<>();
    for (QuizResult result : results) {
      QuizAttempt attempt = result.getQuizAttempt();
      Long contentId = attempt.getQuizSet().getContent().getId();

      records.add(
          DashboardResponse.RecentRecord.builder()
              .reportId(result.getId())
              .quizId(attempt.getQuizSet().getId())
              .title(attempt.getQuizSet().getContent().getTitle())
              .accuracyRate(result.getAccuracyRate())
              .correctCount(result.getCorrectCount())
              .totalCount(result.getTotalCount())
              .completedAt(result.getCompletedAt())
              .tags(tagsByContentId.getOrDefault(contentId, List.of()))
              .build());
    }
    return records;
  }

  private Map<Long, List<DashboardResponse.TagInfo>> fetchTagsByContentId(List<Long> contentIds) {
    if (contentIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, List<DashboardResponse.TagInfo>> tagsByContentId = new LinkedHashMap<>();
    contentTagRepository
        .findByContentIdInWithTag(contentIds)
        .forEach(
            contentTag ->
                tagsByContentId
                    .computeIfAbsent(contentTag.getContent().getId(), ignored -> new ArrayList<>())
                    .add(
                        DashboardResponse.TagInfo.builder()
                            .tagId(contentTag.getTag().getId())
                            .name(contentTag.getTag().getName())
                            .build()));
    return tagsByContentId;
  }

  private BigDecimal calculateAccuracyRate(Integer correctCount, Integer totalCount) {
    if (correctCount == null || totalCount == null || totalCount == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(correctCount)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
  }
}
