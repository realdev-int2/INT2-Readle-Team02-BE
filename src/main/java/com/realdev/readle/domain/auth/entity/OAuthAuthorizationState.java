package com.realdev.readle.domain.auth.entity;

import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "oauth_authorization_state",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_oauth_authorization_state_hash", columnNames = "state_hash")
    },
    indexes = {
      @Index(name = "idx_oauth_authorization_state_expiry", columnList = "expires_at"),
      @Index(
          name = "idx_oauth_authorization_state_provider_expiry",
          columnList = "oauth_provider, expires_at")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAuthorizationState extends BaseCreatedAtEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "state_hash", nullable = false, length = 64)
  private String stateHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "oauth_provider", nullable = false, length = 20)
  private OAuthProvider oauthProvider;

  @Column(name = "return_to", nullable = false, length = 2048)
  private String returnTo;

  @Column(name = "code_verifier_ciphertext", nullable = false, length = 1024)
  private String codeVerifierCiphertext;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "used_at")
  private LocalDateTime usedAt;

  private OAuthAuthorizationState(
      String stateHash,
      OAuthProvider oauthProvider,
      String returnTo,
      String codeVerifierCiphertext,
      LocalDateTime expiresAt) {
    this.stateHash = stateHash;
    this.oauthProvider = oauthProvider;
    this.returnTo = returnTo;
    this.codeVerifierCiphertext = codeVerifierCiphertext;
    this.expiresAt = expiresAt;
  }

  public static OAuthAuthorizationState create(
      String stateHash,
      OAuthProvider oauthProvider,
      String returnTo,
      String codeVerifierCiphertext,
      LocalDateTime expiresAt) {
    return new OAuthAuthorizationState(
        stateHash, oauthProvider, returnTo, codeVerifierCiphertext, expiresAt);
  }

  public boolean isUsableAt(LocalDateTime now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void consume(LocalDateTime now) {
    this.usedAt = now;
  }
}
