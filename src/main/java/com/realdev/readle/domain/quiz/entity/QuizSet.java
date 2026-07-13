package com.realdev.readle.domain.quiz.entity;

import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.ContentValidation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_set")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSet extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuizSetStatus status;

    @Column(name = "question_count")
    private Short questionCount;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "is_bypassed", nullable = false)
    private boolean bypassed;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_validation_id", nullable = false)
    private ContentValidation sourceValidation;
}
