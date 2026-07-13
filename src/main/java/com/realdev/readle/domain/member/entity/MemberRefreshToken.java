package com.realdev.readle.domain.member.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "member_refresh_token",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_member_refresh_token_hash", columnNames = "token_hash")
    },
    indexes = {
      @Index(name = "idx_member_refresh_token_member", columnList = "member_id"),
      @Index(name = "idx_member_refresh_token_expires", columnList = "expires_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRefreshToken extends BaseCreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  private MemberRefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
    this.member = member;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public static MemberRefreshToken create(
      Member member, String tokenHash, LocalDateTime expiresAt) {
    return new MemberRefreshToken(member, tokenHash, expiresAt);
  }

  public void revoke() {
    this.revokedAt = LocalDateTime.now();
  }
}
