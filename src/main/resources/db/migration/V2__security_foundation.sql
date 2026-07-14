CREATE TABLE oauth_authorization_state (
  id BIGINT NOT NULL AUTO_INCREMENT,
  state_hash VARCHAR(64) NOT NULL,
  oauth_provider VARCHAR(20) NOT NULL,
  return_to VARCHAR(2048) NOT NULL,
  code_verifier_ciphertext VARCHAR(1024) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uq_oauth_authorization_state_hash UNIQUE (state_hash),
  CONSTRAINT chk_oauth_authorization_state_provider CHECK (oauth_provider IN ('GOOGLE', 'KAKAO')),
  INDEX idx_oauth_authorization_state_expiry (expires_at),
  INDEX idx_oauth_authorization_state_provider_expiry (oauth_provider, expires_at)
)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='OAuth authorization state and encrypted PKCE verifier';
