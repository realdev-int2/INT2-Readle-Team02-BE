package com.realdev.readle.domain.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "quiz_answer",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_answer_attempt_question", columnNames = {"attempt_id", "question_id"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion quizQuestion;

    @Lob
    @Column(name = "submitted_answer_text")
    private String submittedAnswerText;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_choice_id")
    private QuizChoice submittedChoice;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Lob
    @Column(name = "ai_feedback")
    private String aiFeedback;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;
}
