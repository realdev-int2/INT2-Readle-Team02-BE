package com.realdev.readle.domain.tag.entity;

import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "tag",
    uniqueConstraints = {@UniqueConstraint(name = "uq_tag_name", columnNames = "name")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseCreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  private Tag(String name) {
    this.name = name;
  }

  public static Tag create(String name) {
    return new Tag(name);
  }
}
