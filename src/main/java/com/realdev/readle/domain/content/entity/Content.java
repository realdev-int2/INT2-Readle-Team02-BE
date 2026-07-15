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

  @Column(name = "raw_text", columnDefinition = "LONGTEXT")
  private String rawText;

  @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
  private String extractedText;

  @Enumerated(EnumType.STRING)
  @Column(name = "crawl_status", nullable = false, length = 20)
  private CrawlStatus crawlStatus;

  private Content(
      Member member,
      String title,
      InputType inputType,
      CrawlStatus crawlStatus,
      String originalUrl,
      String rawText,
      String extractedText) {
    this.member = member;
    this.title = title;
    this.inputType = inputType;
    this.crawlStatus = crawlStatus;
    this.originalUrl = originalUrl;
    this.rawText = rawText;
    this.extractedText = extractedText;
  }

  public static Content fromText(Member member, String title, String rawText) {
    return new Content(
        member, title, InputType.TEXT, CrawlStatus.NOT_APPLICABLE, null, rawText, null);
  }

  public static Content fromUrl(
      Member member, String title, String originalUrl, String extractedText) {
    return new Content(
        member, title, InputType.URL, CrawlStatus.SUCCESS, originalUrl, null, extractedText);
  }

  public void updateCrawlResult(String extractedText, CrawlStatus newStatus) {
    // URL 타입이 아닌 콘텐츠에 크롤링 결과를 업데이트하려는 시도를 방지
    if (this.inputType != InputType.URL) {
      return;
    }
    this.extractedText = extractedText;
    this.crawlStatus = newStatus;
  }
}
