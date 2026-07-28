# Local Development

## 1. 환경 변수

```bash
cp .env.example .env
```

복사한 `.env`만으로 로컬 MySQL·LocalStack 연결과 애플리케이션 기동은 가능하다. 다만 실제 외부 연동을 사용하려면 아래 값을 채운다.

- `CLAUDE_API_KEY`: AI 콘텐츠 검증, 퀴즈 생성, AI 채점
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`: Google 로그인
- `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`: Kakao 로그인

OAuth 제공자 콘솔에는 각각 `http://localhost:8080/api/auth/google/callback`, `http://localhost:8080/api/auth/kakao/callback`을 Redirect URI로 등록한다.

### 로컬 비밀값 생성 (선택)

예시의 MySQL 비밀번호와 보안 키는 로컬 전용 고정값이다. 개인 환경에서 교체하려면 아래 명령으로 값을 생성한 뒤 `.env`의 해당 값과 `SPRING_DATASOURCE_PASSWORD`를 함께 바꾼다.

```bash
openssl rand -hex 24      # MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD
openssl rand -base64 48  # JWT_SECRET (32바이트 이상)
openssl rand -base64 32  # OAUTH_STATE_ENCRYPTION_KEY (32바이트 AES 키)
```

생성 결과를 `.env`의 값으로 한 번만 붙여 넣는다. `.env`에 `$(openssl ...)` 형태로 넣으면 애플리케이션을 실행할 때마다 키가 바뀌므로 사용하지 않는다.

```env
OAUTH_STATE_ENCRYPTION_KEY=<openssl rand -base64 32 출력값>
```

`OAUTH_STATE_ENCRYPTION_KEY`는 반드시 표준 Base64로 인코딩된 16·24·32바이트 AES 키여야 한다. 키를 교체하면 아직 소비되지 않은 OAuth state의 콜백은 실패할 수 있다. 생성한 `.env`는 저장소에 커밋하지 않는다.

`local` 프로필은 LocalStack S3를 사용하고, `prod` 프로필은 EC2 IAM Role 자격 증명을 사용한다.
Prod 환경에 AWS access key/secret key를 넣지 않는다.

## 2. 로컬 인프라 실행

```bash
docker compose --env-file .env up -d mysql localstack
```

LocalStack은 시작 시 `S3_BUCKET` 버킷과 CORS를 생성한다.

## 3. 로컬 백엔드 실행

Compose가 실행된 뒤 호스트에서 백엔드를 실행한다. `.env.example`의 `SPRING_DATASOURCE_URL`은 호스트의 MySQL 포트(`localhost:3306`)를 사용한다.

```bash
set -a
source .env
set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun --no-daemon
```

`mysql` 호스트명은 Docker 네트워크 내부에서만 사용한다.

## 4. 백엔드 검증

```bash
./gradlew spotlessCheck test build --no-daemon
```

## 5. Git hook

```bash
pre-commit install --hook-type pre-commit --hook-type pre-push
```

Pre-commit은 Spotless와 gitleaks staged scan을 수행한다.

## 6. OAuth 랜딩 부트스트랩

- OAuth 콜백은 토큰을 URL에 포함하지 않으며, HttpOnly refresh cookie만 설정한다.
- 랜딩 후 클라이언트는 먼저 공개 `GET /api/auth/session`을 호출한다. 이는 서버 세션이 아니며, 읽을 수 있는 `XSRF-TOKEN` cookie를 초기화한다.
- `POST /api/auth/refresh` 전에는 클라이언트가 해당 cookie 값을 `X-XSRF-TOKEN` 헤더에 복사한다(double-submit CSRF).
- refresh로 받은 access token은 메모리에만 보관하고 URL에는 절대 노출하지 않는다.
- 이 흐름은 동일 출처 토폴로지(edge Nginx / Vite proxy)를 전제로 한다.
