# Contributing

## 사전 요구사항

JDK 17과 Docker Desktop(Docker Compose 포함)이 필요하다. Gradle은 Wrapper를 사용하므로 별도 설치하지 않는다.

macOS:

```bash
brew install pre-commit gitleaks
```

Windows PowerShell:

```powershell
py -m pip install pre-commit
winget install --id Gitleaks.Gitleaks --exact
```

설치 후 `pre-commit --version`과 `gitleaks version`이 실행되어야 한다.

## 시작하기

macOS:

```bash
cp .env.example .env
docker compose --env-file .env up -d mysql localstack
pre-commit install --hook-type pre-commit --hook-type pre-push
./gradlew spotlessCheck test build --no-daemon
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d mysql localstack
pre-commit install --hook-type pre-commit --hook-type pre-push
.\gradlew.bat spotlessCheck test build --no-daemon
```

로컬 인프라와 환경 변수의 상세 내용은 [docs/LOCAL_DEV.md](docs/LOCAL_DEV.md)를 참고한다. 현재 Compose는 MySQL과 LocalStack만 제공한다. 백엔드 컨테이너 실행 절차는 Dockerfile과 서비스 정의를 추가할 때 함께 문서화한다. `.env`와 인증 정보는 Git에 커밋하지 않는다.

## 작업 흐름

1. GitHub Issue를 생성한다.
2. 이슈 기반 브랜치를 만든다: `feat/{이슈번호}-{작업명}`, `fix/{이슈번호}-{작업명}`.
3. 작업 후 품질 검증을 통과시킨다.
4. `main`을 대상으로 PR을 만들고 최소 1명의 승인 후 병합한다. `main`에 직접 push하지 않는다.

커밋은 `feat`, `fix`, `refactor`, `style`, `docs`, `test`, `chore`, `ci` 타입을 사용하고 이슈 번호를 포함한다.

```text
feat: 로그인 기능 구현 (#10)
```

PR에는 변경 사항, 기술적 결정과 검증 결과, 연관 이슈를 기록한다. 제공된 Issue/PR 템플릿을 사용한다.

## 코드 규칙

- Java 코드는 Spotless의 `google-java-format` 결과를 따른다. 수동 포매팅보다 formatter 결과를 우선한다.
- 도메인 코드는 `domain/{도메인}/controller`, `service`, `repository`, `entity`, `exception`, `dto/request`, `dto/response` 구조를 따른다. 공통 설정과 보안은 `global/`, 외부 AI 연동은 `infra/ai/`에 둔다.
- Controller는 Repository에 직접 의존하지 않고, Entity를 API 응답으로 반환하지 않는다. Request/Response DTO를 분리한다.
- Service는 Controller/Web에, Repository는 Service에 의존하지 않는다. Entity는 Controller, DTO, Config, Security, Web 계층에 의존하지 않는다. 이 규칙은 ArchUnit 테스트로 검증한다.
- Entity/VO는 정적 팩토리를 우선하고, Entity의 `@Setter`와 Lombok `@Data`는 사용하지 않는다. 상태 변경은 의도가 드러나는 메서드로 표현한다.
- 조회는 `@Transactional(readOnly = true)`를 사용하고 트랜잭션 경계는 Service에 둔다.
- 로그와 예외 응답에 비밀번호, 토큰, JWT, 개인정보, AI 프롬프트 원문을 남기지 않는다.

## 검증

커밋 전에는 아래 명령을 실행한다.

```bash
./gradlew spotlessCheck test build --no-daemon
```

Windows PowerShell에서는 아래 명령을 사용한다.

```powershell
.\gradlew.bat spotlessCheck test build --no-daemon
```

pre-commit은 Spotless와 staged secret scan을, pre-push는 Spotless·테스트·빌드를 실행한다. 새 기능은 핵심 비즈니스 로직 테스트를 포함하고, DB 조회 변경 시 N+1 및 실행 계획을 확인한다.
