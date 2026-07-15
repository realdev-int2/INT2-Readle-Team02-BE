package com.realdev.readle.domain.content.entity;

import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "content_validation",
    indexes = {
      @Index(name = "idx_content_validation_content_created", columnList = "content_id, created_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentValidation extends BaseCreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @Enumerated(EnumType.STRING)
  @Column(name = "validation_method", nullable = false, length = 20)
  private ValidationMethod validationMethod;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 10)
  private ValidationStatus status;

  @Column(name = "validation_score", precision = 5, scale = 2)
  private BigDecimal validationScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "reject_reason_code", length = 30)
  private RejectReasonCode rejectReasonCode;

  @Column(name = "evidence_snippets", columnDefinition = "TEXT")
  private String evidenceSnippets;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_code", length = 30)
  private ErrorCode errorCode;

  @Column(name = "validated_at")
  private LocalDateTime validatedAt;

  public void markPassed(BigDecimal validationScore) {
    this.status = ValidationStatus.PASSED;
    this.validationScore = validationScore;
    this.validatedAt = LocalDateTime.now();
  }

  public void markRejected(
      BigDecimal validationScore, RejectReasonCode rejectReasonCode, String evidenceSnippets) {
    this.status = ValidationStatus.REJECTED;
    this.validationScore = validationScore;
    this.rejectReasonCode = rejectReasonCode;
    this.evidenceSnippets = evidenceSnippets;
    this.validatedAt = LocalDateTime.now();
  }

  public void markFailed(ErrorCode errorCode) {
    this.status = ValidationStatus.FAILED;
    this.errorCode = errorCode;
    this.validatedAt = LocalDateTime.now();
  }

  @Builder
  private ContentValidation(
      Content content,
      ValidationMethod validationMethod,
      ValidationStatus status,
      BigDecimal validationScore,
      RejectReasonCode rejectReasonCode,
      String evidenceSnippets,
      ErrorCode errorCode,
      LocalDateTime validatedAt) {
    this.content = content;
    this.validationMethod = validationMethod;
    this.status = status;
    this.validationScore = validationScore;
    this.rejectReasonCode = rejectReasonCode;
    this.evidenceSnippets = evidenceSnippets;
    this.errorCode = errorCode;
    this.validatedAt = validatedAt;
  }
}
