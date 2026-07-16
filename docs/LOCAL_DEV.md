# Local Development

## 1. 환경 변수

```bash
cp .env.example .env
```

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
