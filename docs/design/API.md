# API 설계

## 상태

이 문서는 현재 backend controller, DTO, security 설정을 기준으로 정리한 API 목록이다. OpenAPI 최종 산출물이 아니라 backend-local 설계 문서다.

## 공통 규칙

- API base path는 `/api`다.
- OAuth provider는 `google`, `kakao` path 값을 받으며 내부 enum은 `GOOGLE`, `KAKAO`다.
- 보호 API는 `Authorization: Bearer <access-token>` 인증을 사용한다.
- Auth refresh/session 응답은 `{ "data": ... }` envelope를 사용한다.
- Content, Quiz, Result Report, Dashboard controller 응답은 DTO를 최상위 body로 반환한다.
- 오류 응답은 공통 envelope를 사용한다.

```json
{
  "error": {
    "code": "INVALID_INPUT",
    "message": "요청 값을 확인해 주세요.",
    "details": []
  }
}
```

## 인증 API

| Method | Path | Auth | 응답 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/{provider}/start` | Public | OAuth provider authorization URL로 `302` redirect |
| `GET` | `/api/auth/{provider}/callback` | Public | refresh cookie 설정 후 safe `returnTo`로 `302` redirect |
| `POST` | `/api/auth/refresh` | Public + refresh cookie + CSRF | `{ "data": { "accessToken": "..." } }` |
| `POST` | `/api/auth/logout` | Required | `204 No Content`, refresh cookie 삭제 |
| `GET` | `/api/auth/session` | Public | `{ "data": { "authenticated": true, "uuid": "..." } }` |
| `GET` | `/api/users/me` | Required | `{ "data": { "uuid": "...", "nickname": "...", "profileImageUrl": "..." } }` |

## 콘텐츠 API

| Method | Path | Auth | 요청/응답 |
| --- | --- | --- | --- |
| `POST` | `/api/contents/extract` | Required | request `{ "url": "..." }`, response `{ "title": "...", "content": "..." }` |
| `POST` | `/api/contents` | Required | request `inputType`, `title`, `url`, `extractedText`, `text`; response `201` with `contentId`, `validationStatus` |
| `GET` | `/api/contents/{contentId}/validation` | Required | `contentId`, `status`, `errorCode`, `message`, `bypassAvailable`, `requestedAt`, `validatedAt` |
| `POST` | `/api/contents/{contentId}/validation/retry` | Required | 새 검증 이력을 만들고 validation 응답 반환 |

`validationStatus` 값은 `PENDING`, `PASSED`, `REJECTED`, `FAILED`다. `POST /api/contents` 성공은 검증 통과를 의미하지 않는다.

## 퀴즈 API

| Method | Path | Auth | 요청/응답 |
| --- | --- | --- | --- |
| `POST` | `/api/quizzes` | Required | request `{ "sourceValidationId": 1 }`, response `201` with `quizId`, `status`, `questionCount`, `createdAt` |
| `POST` | `/api/quizzes/{quizSetId}/attempts` | Required | 새 풀이 attempt 생성 |
| `GET` | `/api/quizzes/attempts/{attemptId}` | Required | attempt 상태와 문제 목록 조회 |
| `POST` | `/api/quizzes/attempts/{attemptId}/submit` | Required | 답안 제출, 채점, 결과 저장 |
| `GET` | `/api/quizzes/attempts/{attemptId}/result` | Required | 완료된 attempt 결과 조회 |

`POST /api/quizzes`는 `sourceValidationId`가 가리키는 `content_validation` row를 기준으로 생성한다. 검증 상태가 `PASSED`이거나 AI `REJECTED` 우회 가능 상태일 때만 진행한다.

## 결과 리포트와 대시보드

| Method | Path | Auth | 요청/응답 |
| --- | --- | --- | --- |
| `GET` | `/api/result-reports` | Required | query `cursor`, `size`, `sort`, `tagId`; response `content`, `size`, `nextCursor`, `hasNext` |
| `GET` | `/api/result-reports/{reportId}` | Required | 결과 리포트 상세 |
| `GET` | `/api/dashboard` | Required | `totals`, `tagSummaries`, `recentRecords` |

목록 기본값은 `size=10`, `sort=latest`이고 `size` 최대값은 `50`이다.

## 운영 API

| Method | Path | Auth | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/actuator/health/**` | Public | readiness/liveness 확인 |
| `GET` | `/api/actuator/prometheus` | Prometheus Basic Auth 또는 deny | metrics scrape endpoint |

`prod` 프로필은 management base path를 `/api/actuator`로 설정한다. Prometheus endpoint는 `security.prometheus-metrics.enabled=false` 기본값이면 차단된다.

## 관련 문서

- [인증 설계](AUTH_DESIGN.md)
- [AI 설계](AI_DESIGN.md)
- [ERD](ERD.md)
- [ADR-007](../adr/ADR-007.md)
- [ADR-009](../adr/ADR-009.md)
- [ADR-013](../adr/ADR-013.md)
- [ADR-020](../adr/ADR-020.md)
