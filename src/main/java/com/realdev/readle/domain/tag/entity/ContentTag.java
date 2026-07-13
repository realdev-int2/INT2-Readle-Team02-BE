package com.realdev.readle.domain.tag.entity;

import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "content_tag",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_content_tag",
          columnNames = {"content_id", "tag_id"})
    },
    indexes = {@Index(name = "idx_content_tag_tag_content", columnList = "tag_id, content_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTag extends BaseCreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  private ContentTag(Content content, Tag tag) {
    this.content = content;
    this.tag = tag;
  }

  public static ContentTag create(Content content, Tag tag) {
    return new ContentTag(content, tag);
  }
}
