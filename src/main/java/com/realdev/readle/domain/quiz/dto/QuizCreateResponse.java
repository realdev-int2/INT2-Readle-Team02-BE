package com.realdev.readle.domain.quiz.dto;

import com.realdev.readle.domain.quiz.entity.QuizSet;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class QuizCreateResponse {

    private Long quizId;
    private String status;
    private Integer questionCount;
    private LocalDateTime createdAt;

    public static QuizCreateResponse from(QuizSet quizSet) {
        return QuizCreateResponse.builder()
                .quizId(quizSet.getId())
                .status(quizSet.getStatus().name().toLowerCase())
                .questionCount(quizSet.getQuestionCount())
                .createdAt(quizSet.getCreatedAt())
                .build();
    }
}
