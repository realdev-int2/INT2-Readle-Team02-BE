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

## 3. 백엔드 검증

```bash
./gradlew spotlessCheck test build --no-daemon
```

## 4. Git hook

```bash
pre-commit install --hook-type pre-commit --hook-type pre-push
```

Pre-commit은 Spotless와 gitleaks staged scan을 수행한다.
