-- =====================================================
-- Readle Database Schema
-- Version : v1.0
-- Last Updated : 2026-07-13
-- Team : 와진짜개발자같다
-- Project : Readle
-- Database : MySQL 8.0
-- =====================================================

-- =====================================================
-- Table: member
-- =====================================================

CREATE TABLE member (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 고유 식별자 (내부용)',

uuid CHAR(36) NOT NULL COMMENT '외부 노출용 식별자 (JWT sub 등)',

oauth_provider VARCHAR(20) NOT NULL COMMENT 'OAuth 제공자 (GOOGLE, KAKAO)',
oauth_id VARCHAR(255) NOT NULL COMMENT 'OAuth Provider 사용자 ID',

email VARCHAR(255) NULL COMMENT '이메일',
nickname VARCHAR(30) NOT NULL COMMENT '닉네임',
profile_image_url VARCHAR(300) NULL COMMENT '프로필 이미지 URL',

created_at DATETIME NOT NULL COMMENT '계정 생성 시각',
updated_at DATETIME NOT NULL COMMENT '수정 시각',
last_login_at DATETIME NULL COMMENT '최근 로그인',

PRIMARY KEY (id),

CONSTRAINT uq_member_uuid
    UNIQUE (uuid),

CONSTRAINT uq_member_oauth
    UNIQUE (oauth_provider, oauth_id),

INDEX idx_member_email (email),

CONSTRAINT chk_member_oauth_provider
    CHECK (
        oauth_provider IN ('GOOGLE', 'KAKAO')
    )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='OAuth 기반 회원';

-- =====================================================
-- Table: member_refresh_token
-- =====================================================

CREATE TABLE member_refresh_token (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Refresh Token 식별자',

member_id BIGINT NOT NULL COMMENT '소유 회원',

token_hash VARCHAR(255) NOT NULL COMMENT 'Refresh Token 해시값',

expires_at DATETIME NOT NULL COMMENT '만료 시각',

created_at DATETIME NOT NULL COMMENT '발급 시각',

revoked_at DATETIME NULL COMMENT '무효화 시각(로그아웃/재발급), NULL이면 유효',

PRIMARY KEY (id),

CONSTRAINT fk_member_refresh_token_member
    FOREIGN KEY (member_id)
    REFERENCES member(id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT,

CONSTRAINT uq_member_refresh_token_hash
    UNIQUE (token_hash),

INDEX idx_member_refresh_token_member (member_id),

INDEX idx_member_refresh_token_expires (expires_at)


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='Refresh Token 저장 (MySQL 기반, cleanup job으로 만료 관리)';

-- =====================================================
-- Table: content
-- =====================================================

CREATE TABLE content (

id BIGINT NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 ID',

member_id BIGINT NOT NULL COMMENT '작성자',

title VARCHAR(255) NOT NULL COMMENT '콘텐츠 제목(URL 메타데이터 또는 사용자 입력)',

input_type VARCHAR(10) NOT NULL COMMENT '입력 방식',

original_url VARCHAR(500) NULL COMMENT '원본 URL',

raw_text LONGTEXT NULL COMMENT '사용자 입력 원문',

extracted_text LONGTEXT NULL COMMENT '크롤링 추출 본문',

crawl_status VARCHAR(20) NOT NULL COMMENT '크롤링 상태',

created_at DATETIME NOT NULL COMMENT '콘텐츠 생성 시각',

updated_at DATETIME NOT NULL COMMENT '수정 시각',

PRIMARY KEY (id),

CONSTRAINT fk_content_member
    FOREIGN KEY (member_id)
    REFERENCES member(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

INDEX idx_content_member_created (
    member_id,
    created_at
),

CONSTRAINT chk_content_input_type
    CHECK (
        input_type IN (
            'URL',
            'TEXT'
        )
    ),

CONSTRAINT chk_content_crawl_status
    CHECK (
        crawl_status IN (
            'NOT_APPLICABLE',
            'SUCCESS',
            'FAILED'
        )
    )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='사용자 학습 콘텐츠';

-- =====================================================
-- Table: content_validation
-- =====================================================

CREATE TABLE content_validation (

id BIGINT NOT NULL AUTO_INCREMENT COMMENT '검증 이력 ID',

content_id BIGINT NOT NULL COMMENT '검증 대상 콘텐츠',

validation_method VARCHAR(20) NOT NULL COMMENT '적합성 판단 방식 (AI, WHITELIST, STATIC_GUARDRAIL)',

status VARCHAR(10) NOT NULL COMMENT '검증 상태',

validation_score DECIMAL(5,2) NULL COMMENT 'AI 적합성 점수(0~100). AI 판단이 아니면 NULL',

reject_reason_code VARCHAR(30) NULL COMMENT '부적합 사유 코드',

evidence_snippets TEXT NULL COMMENT '판정 근거(JSON Array)',

error_code VARCHAR(30) NULL COMMENT '오류 코드',

created_at DATETIME NOT NULL COMMENT '검증 요청 시각',

validated_at DATETIME NULL COMMENT '검증 완료 시각',

PRIMARY KEY (id),

CONSTRAINT fk_content_validation_content
    FOREIGN KEY (content_id)
    REFERENCES content(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

INDEX idx_content_validation_content_created (
    content_id,
    created_at
),

CONSTRAINT chk_content_validation_status
    CHECK (
        status IN (
            'PENDING',
            'PASSED',
            'REJECTED',
            'FAILED'
        )
    ),

CONSTRAINT chk_content_validation_method
    CHECK (
        validation_method IN (
            'AI',
            'WHITELIST',
            'STATIC_GUARDRAIL'
        )
    ),

CONSTRAINT chk_content_validation_reject_reason_code
  CHECK (
      reject_reason_code IN (
          'EMPTY_CONTENT',
          'CONTENT_TOO_SHORT',
          'BAD_WORD',
          'PROMPT_INJECTION_DETECTED',
          'NOT_DEVELOPMENT_RELATED',
          'LOW_CONFIDENCE'
      )
      OR reject_reason_code IS NULL
    ),

CONSTRAINT chk_content_validation_error_code
  CHECK (
      error_code IN (
        'AI_SERVICE_ERROR',
        'TIMEOUT',
        'UNKNOWN_ERROR'
      )
      OR error_code IS NULL
  ),

CONSTRAINT chk_validation_score
  CHECK (
    validation_score >= 0 AND validation_score <= 100
  )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='콘텐츠 검증 이력';

-- =========================================================
-- TABLE : quiz_set
-- =========================================================

CREATE TABLE quiz_set (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '퀴즈 세트 ID',

content_id BIGINT NOT NULL COMMENT '퀴즈 생성 대상 콘텐츠',

status VARCHAR(20) NOT NULL COMMENT '퀴즈 생성 상태',

question_count SMALLINT NULL COMMENT '생성된 문제 개수(1~5)',

created_at DATETIME NOT NULL COMMENT '퀴즈 생성 요청 시각',
completed_at DATETIME NULL COMMENT '퀴즈 생성 완료 시각',

is_bypassed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '검증 우회 생성 여부',

source_validation_id BIGINT NOT NULL COMMENT '이 퀴즈 생성의 근거가 된 검증 이력',

PRIMARY KEY (id),

CONSTRAINT fk_quiz_set_content
    FOREIGN KEY (content_id)
    REFERENCES content(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT fk_quiz_set_source_validation
    FOREIGN KEY (source_validation_id)
    REFERENCES content_validation(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT uq_quiz_set_source_validation
    UNIQUE (source_validation_id),

CONSTRAINT chk_quiz_set_status
    CHECK (
        status IN (
            'GENERATING',
            'COMPLETED',
            'FAILED'
        )
    ),

CONSTRAINT chk_question_count
  CHECK (
    question_count >= 1 AND question_count <= 5
  )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='콘텐츠 기반 퀴즈 세트';

-- =========================================================
-- TABLE : quiz_question
-- =========================================================

CREATE TABLE quiz_question (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '퀴즈 개별 문제 ID',

quiz_set_id BIGINT NOT NULL COMMENT '소속 퀴즈 세트',

question_type VARCHAR(20) NOT NULL
    COMMENT 'MULTIPLE_CHOICE | SHORT_ANSWER | CODE_BLANK',

order_no SMALLINT NOT NULL COMMENT '문제 순서',

question_text TEXT NOT NULL,

code_snippet TEXT NULL
    COMMENT 'CODE_BLANK 전용',

correct_answer TEXT NULL
    COMMENT 'SHORT_ANSWER / CODE_BLANK 전용',

explanation TEXT NULL
    COMMENT '내부 AI 피드백 생성용',

source_excerpt TEXT NULL
    COMMENT '문제 생성 근거',

PRIMARY KEY (id),

CONSTRAINT fk_question_quiz_set
    FOREIGN KEY (quiz_set_id)
    REFERENCES quiz_set(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT uq_question_order
    UNIQUE (quiz_set_id, order_no),

CONSTRAINT chk_question_type
    CHECK (
        question_type IN (
            'MULTIPLE_CHOICE',
            'SHORT_ANSWER',
            'CODE_BLANK'
        )
    )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='퀴즈 개별 문제';

-- =========================================================
-- TABLE : quiz_choice
-- =========================================================

CREATE TABLE quiz_choice (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '객관식 문제 선택지 ID',

question_id BIGINT NOT NULL,

order_no SMALLINT NOT NULL,

choice_text TEXT NOT NULL,

is_correct BOOLEAN NOT NULL DEFAULT FALSE COMMENT '정답 여부',

PRIMARY KEY (id),

CONSTRAINT fk_choice_question
    FOREIGN KEY (question_id)
    REFERENCES quiz_question(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT uq_choice_order
    UNIQUE (question_id, order_no)


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='객관식 문제 선택지';

-- =========================================================
-- TABLE : quiz_attempt
-- =========================================================

CREATE TABLE quiz_attempt (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '퀴즈 풀이 진행 상태 ID',

quiz_set_id BIGINT NOT NULL,

member_id BIGINT NOT NULL,

status VARCHAR(20) NOT NULL
    COMMENT 'IN_PROGRESS | SUBMITTED',

started_at DATETIME NOT NULL,

submitted_at DATETIME NULL,

PRIMARY KEY (id),

CONSTRAINT fk_attempt_quiz_set
    FOREIGN KEY (quiz_set_id)
    REFERENCES quiz_set(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT fk_attempt_member
    FOREIGN KEY (member_id)
    REFERENCES member(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

INDEX idx_attempt_member_started (member_id, started_at),

CONSTRAINT chk_attempt_status
    CHECK (
        status IN (
            'IN_PROGRESS',
            'SUBMITTED'
        )
    )


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='사용자 퀴즈 풀이 진행 상태';

-- =========================================================
-- TABLE : quiz_answer
-- =========================================================

CREATE TABLE quiz_answer (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '문제별 사용자 답안 및 채점 결과 ID',

attempt_id BIGINT NOT NULL,

question_id BIGINT NOT NULL,

submitted_answer_text TEXT NULL
    COMMENT '주관식 / 코드빈칸 답안',

submitted_choice_id BIGINT NULL
    COMMENT '객관식 답안',

is_correct BOOLEAN NOT NULL COMMENT '채점 결과',

ai_feedback TEXT NULL
    COMMENT '오답 AI 피드백',

evaluated_at DATETIME NOT NULL COMMENT '채점 완료 시각',

PRIMARY KEY (id),

CONSTRAINT fk_answer_attempt
    FOREIGN KEY (attempt_id)
    REFERENCES quiz_attempt(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT fk_answer_question
    FOREIGN KEY (question_id)
    REFERENCES quiz_question(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT fk_answer_choice
    FOREIGN KEY (submitted_choice_id)
    REFERENCES quiz_choice(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT uq_answer_attempt_question
    UNIQUE (attempt_id, question_id)


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='문제별 사용자 답안 및 채점 결과';

-- =========================================================
-- TABLE : quiz_result
-- =========================================================

CREATE TABLE quiz_result (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '퀴즈 결과 리포트 ID',

attempt_id BIGINT NOT NULL,

accuracy_rate DECIMAL(5,2) NOT NULL
    COMMENT '정답률(%). correct_count / total_count * 100',

correct_count SMALLINT NOT NULL,

total_count SMALLINT NOT NULL,

solve_duration_seconds INT NOT NULL
    COMMENT '사용자 실제 풀이 시간(초)',

completed_at DATETIME NOT NULL,

PRIMARY KEY (id),

CONSTRAINT uq_result_attempt
UNIQUE (attempt_id),

CONSTRAINT fk_result_attempt
    FOREIGN KEY (attempt_id)
    REFERENCES quiz_attempt(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='퀴즈 결과 리포트';

-- =========================================================
-- TABLE : tag
-- =========================================================

CREATE TABLE tag (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '태그 마스터 ID',

name VARCHAR(50) NOT NULL COMMENT '태그명',

created_at DATETIME NOT NULL,

PRIMARY KEY (id),

CONSTRAINT uq_tag_name
    UNIQUE (name)


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='AI가 생성한 태그 마스터';

-- =========================================================
-- TABLE : content_tag
-- =========================================================

CREATE TABLE content_tag (
id BIGINT NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 태그 ID',

content_id BIGINT NOT NULL,

tag_id BIGINT NOT NULL,

created_at DATETIME NOT NULL,

PRIMARY KEY (id),

CONSTRAINT fk_content_tag_content
    FOREIGN KEY (content_id)
    REFERENCES content(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT fk_content_tag_tag
    FOREIGN KEY (tag_id)
    REFERENCES tag(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

CONSTRAINT uq_content_tag
    UNIQUE (content_id, tag_id),

INDEX idx_content_tag_tag_content (tag_id, content_id)


)
ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='콘텐츠와 태그 연결';
