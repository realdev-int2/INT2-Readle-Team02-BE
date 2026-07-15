-- V5: content_validation.error_code CHECK 제약에 SCHEMA_INVALID 값 추가
-- AI 응답은 정상 수신했으나(호출 자체는 성공) JSON 파싱 실패 또는
-- 검증 스키마(점수 범위, 필수 필드 등) 위반 시 사용하는 에러 코드.
-- MySQL은 제약 조건을 직접 MODIFY할 수 없으므로 기존 제약을 DROP 후 재생성한다.

ALTER TABLE content_validation
DROP CONSTRAINT chk_content_validation_error_code;

ALTER TABLE content_validation
    ADD CONSTRAINT chk_content_validation_error_code
        CHECK (
            error_code IN (
                           'AI_SERVICE_ERROR',
                           'TIMEOUT',
                           'SCHEMA_INVALID',
                           'UNKNOWN_ERROR'
                )
                OR error_code IS NULL
            );