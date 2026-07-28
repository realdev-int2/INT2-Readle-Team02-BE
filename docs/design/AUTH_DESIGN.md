# 인증 설계

## 상태

이 문서는 현재 backend 인증 구현과 설정을 기준으로 한다. 토큰 정책 결정 배경은 [ADR-007](../adr/ADR-007.md), OAuth state 저장소 결정은 [ADR-018](../adr/ADR-018.md)을 따른다.

## 범위

- Kakao, Google OAuth 로그인
- OAuth state와 PKCE verifier의 MySQL 기반 일회성 저장
- backend callback 후 refresh cookie 발급
- refresh cookie 기반 Access Token bootstrap
- JWT 인증 후 RDB 소유권 확인

MVP에는 폼 로그인, 서버 세션 저장소, Redis 인증 저장소, Refresh Token rotation, Access Token denylist, RBAC/admin 정책이 없다.

## 토큰과 쿠키

| 항목 | 현재 구현 |
| --- | --- |
| Access Token | JWT, `security.access-token-minutes=30` |
| Refresh Token | MySQL `member_refresh_token.token_hash` 저장, `security.refresh-token-days=14` |
| Refresh cookie | `__Host-readle_refresh_token`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` |
| OAuth state cookie | `__Host-readle_oauth_state`, `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/` |
| CSRF bootstrap cookie | Spring Security `XSRF-TOKEN`, readable cookie |
| JWT audience | `readle-api` |
| JWT issuer | `security.jwt-issuer` |
| JWT subject | `member.uuid` |

`SecurityProperties`는 JWT secret 최소 32바이트, OAuth state encryption key 16/24/32바이트 AES key, Access Token TTL 1~60분, Refresh Token TTL 1~30일, state TTL 1~10분 범위를 검증한다.

## OAuth 흐름

1. Client가 `GET /api/auth/{provider}/start?returnTo=...`를 호출한다.
2. Backend가 provider allowlist, safe `returnTo`, state, PKCE verifier를 준비한다.
3. Backend가 `oauth_authorization_state`에 state hash와 encrypted verifier를 저장하고 OAuth state cookie를 설정한다.
4. Provider callback에서 request `state`와 browser cookie state가 먼저 일치해야 한다.
5. Backend가 MySQL row를 provider와 state hash로 조회해 미사용/미만료 state를 consume한다.
6. Backend가 code를 provider token endpoint와 교환하고 사용자 정보를 조회한다.
7. Backend가 `member`를 생성/갱신하고 refresh cookie를 설정한 뒤 safe `returnTo`로 redirect한다.
8. Client는 `GET /api/auth/session`으로 `XSRF-TOKEN` cookie를 초기화한다.
9. Client는 `XSRF-TOKEN` 값을 `X-XSRF-TOKEN` header에 넣어 `POST /api/auth/refresh`를 호출하고 Access Token을 받는다.

Access Token은 URL, cookie, DB에 저장하지 않는다. Refresh Token 원문은 DB에 저장하지 않는다.

## 실패 처리

- 시작 실패는 `/login?authError=oauth_failed`로 redirect하고 OAuth state cookie를 삭제한다.
- callback `error=access_denied`는 `/login?authError=oauth_cancelled`로 redirect한다.
- 그 외 callback 실패는 `/login?authError=oauth_failed`로 redirect한다.
- state 불일치, 만료, 재사용, provider 불일치, 저장소 오류는 token 발급 없이 실패한다.
- `/api/auth/refresh`에서 refresh token이 없거나 유효하지 않으면 `INVALID_REFRESH_TOKEN` 오류를 반환한다.

## 보안 경계

- `/api/auth/*/start`, `/api/auth/*/callback`, `/api/auth/refresh`, `/api/auth/session`은 공개 API다.
- `/api/auth/logout`과 `/api/users/me`는 인증이 필요하다.
- `/api/auth/refresh`는 cookie 기반 CSRF 보호를 사용한다.
- 그 외 `/api/**`는 JWT 인증을 요구한다. `/api/actuator/health/**`만 공개된다.
- `/api/actuator/prometheus`는 별도 SecurityFilterChain에서 Basic Auth 또는 deny 정책을 적용한다.

## 관련 구현

- `domain/auth/controller/AuthController.java`
- `domain/auth/service/AuthService.java`
- `domain/auth/service/OAuthStateService.java`
- `domain/auth/service/RefreshTokenService.java`
- `domain/auth/OAuthStateCookie.java`
- `domain/auth/RefreshTokenCookie.java`
- `global/security/SecurityConfig.java`
- `global/security/JwtService.java`
- `src/main/resources/db/migration/V2__security_foundation.sql`
