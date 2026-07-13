package com.realdev.readle.domain.content.entity;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.global.common.entity.BaseTimeEntity;
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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "content",
    indexes = {@Index(name = "idx_content_member_created", columnList = "member_id, created_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "title", nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_type", nullable = false, length = 10)
  private InputType inputType;

  @Column(name = "original_url", length = 500)
  private String originalUrl;

  @Lob
  @Column(name = "raw_text")
  private String rawText;

  @Lob
  @Column(name = "extracted_text")
  private String extractedText;

  @Enumerated(EnumType.STRING)
  @Column(name = "crawl_status", nullable = false, length = 20)
  private CrawlStatus crawlStatus;
}
