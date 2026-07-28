# AI 설계

## 상태

이 문서는 현재 backend AI 호출, 검증, 생성, 채점 구현을 기준으로 한다. 관련 결정 배경은 [ADR-001](../adr/ADR-001.md), [ADR-010](../adr/ADR-010.md), [ADR-011](../adr/ADR-011.md), [ADR-014](../adr/ADR-014.md), [ADR-019](../adr/ADR-019.md), [ADR-020](../adr/ADR-020.md)에 있다.

## 구성

| 항목 | 현재 구현 |
| --- | --- |
| Provider | Anthropic Claude Messages API |
| 설정 prefix | `anthropic.claude` |
| 기본 model | `claude-sonnet-5` |
| API version | `2023-06-01` |
| base URL | `https://api.anthropic.com` |
| max tokens | `4000` |
| prompt 위치 | `src/main/resources/prompts/` |
| URL 추출 | Jsoup 기반 정적 크롤러 |

## 콘텐츠 검증 파이프라인

1. `POST /api/contents`가 `content` row를 만들고 검증을 비동기로 시작한다.
2. `ContentGuardrailService`가 정적 가드레일을 먼저 실행한다.
3. 정적 가드레일에서 걸리면 `validation_method=STATIC_GUARDRAIL`, `status=REJECTED`를 저장한다.
4. 화이트리스트 도메인이면 Claude 호출 없이 `validation_method=WHITELIST`, `status=PASSED`를 저장한다.
5. 그 외에는 `AiValidationService`가 Claude 검증을 실행한다.
6. Client는 `GET /api/contents/{contentId}/validation`으로 상태를 polling한다.

검증 상태는 `PENDING`, `PASSED`, `REJECTED`, `FAILED`다. 오류 코드는 `AI_SERVICE_ERROR`, `TIMEOUT`, `SCHEMA_INVALID`, `UNKNOWN_ERROR`다.

## 정적 가드레일

정적 가드레일은 AI 호출 전에 실행된다.

- 빈 콘텐츠와 최소 길이 미달 검사
- 한국어 비속어 검사
- prompt injection keyword 검사
- safeword whitelist 적용

관련 설정은 `content.validation` 아래에 있다. 현재 최소 길이 기본값은 `300`이고, prompt injection keyword와 whitelist domain 목록은 `application.yaml`에서 관리한다.

## 퀴즈 생성

`POST /api/quizzes`는 `sourceValidationId`를 받아 `content_validation` row를 조회한다.

- `PASSED` 검증은 일반 생성 대상이다.
- AI `REJECTED` 검증은 우회 생성 가능 상태로 처리된다.
- 그 외 상태는 생성하지 않는다.
- `quiz_set.source_validation_id`에는 생성 근거 검증 row를 저장한다.
- 같은 `source_validation_id`에는 하나의 `quiz_set`만 허용한다.

Claude 응답은 `ClaudeQuizResponseDto`로 파싱하고, 문항 수와 문제 유형, 객관식 정답 수, 주관식/코드 빈칸 정답 공백 여부를 검증한 뒤 저장한다. 본문에 코드 신호가 없으면 `CODE_BLANK` 생성을 요청하지 않고, 응답에 포함되어도 저장하지 않는다.

## 채점

`QuizSolveService`는 객관식과 정적 일치 가능한 답안을 먼저 처리하고, 의미 비교가 필요한 주관식/코드 빈칸 답안은 `QuizAiGradingService`와 Claude를 사용한다.

채점 요청은 all-or-nothing으로 처리된다. AI 채점 중 실패하면 답안과 결과 리포트를 부분 저장하지 않고 요청을 실패시킨다.

## 재시도와 timeout

- 일반 Claude generation client는 429 또는 5xx, I/O 장애에 대해 500ms 후 1회 재시도한다.
- 콘텐츠 검증 재시도는 `AiValidationService`와 validation 설정이 담당한다.
- `POST /api/contents/{contentId}/validation/retry`는 기존 실패 이력을 덮어쓰지 않고 새 검증 이력을 만든다.
- `POST /api/quizzes`는 기존 `FAILED` quiz_set을 같은 row에서 retry할 수 있다.

## 계측

현재 backend는 다음 Micrometer metric을 기록한다.

| Metric | 목적 |
| --- | --- |
| `readle.ai.client.requests` | Claude generation 요청 결과 |
| `readle.ai.client.retries` | Claude generation 재시도 |
| `readle.ai.client.tokens` | purpose/type별 token 사용량 |
| `readle.ai.client.tokens.all` | type별 전체 token 사용량 |
| `readle.content.validation` | 콘텐츠 검증 status/method별 duration |
| `readle.quiz.generation` | 퀴즈 생성 outcome |
| `readle.quiz.generation.retries` | 퀴즈 생성 retry |
| `readle.quiz.grading` | 답안 제출부터 AI 채점·점수 저장·결과 리포트 생성까지의 outcome별 duration |
| `readle.content.validation.bypasses` | 검증 우회 생성 |

## 관련 구현

- `global/infrastructure/ai/ClaudeClient.java`
- `global/infrastructure/ai/ClaudeTemplate.java`
- `global/infrastructure/prompt/PromptLoader.java`
- `domain/content/service/ContentGuardrailService.java`
- `domain/content/service/AiValidationService.java`
- `domain/content/service/ContentValidationService.java`
- `domain/quiz/service/QuizGenerationService.java`
- `domain/quiz/service/QuizAiGradingService.java`
- `domain/quiz/service/QuizSolveService.java`
