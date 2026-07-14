package com.realdev.readle.domain.quiz.dto;

import com.realdev.readle.domain.quiz.entity.QuizResult;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class QuizSubmitResponse {
    private final Long resultId;
    private final Long attemptId;
    private final BigDecimal accuracyRate;
    private final Integer correctCount;
    private final Integer totalCount;
    private final Integer solveDurationSeconds;
    private final LocalDateTime completedAt;

    public static QuizSubmitResponse from(QuizResult result) {
        return QuizSubmitResponse.builder()
                .resultId(result.getId())
                .attemptId(result.getQuizAttempt().getId())
                .accuracyRate(result.getAccuracyRate())
                .correctCount(result.getCorrectCount())
                .totalCount(result.getTotalCount())
                .solveDurationSeconds(result.getSolveDurationSeconds())
                .completedAt(result.getCompletedAt())
                .build();
    }
}
