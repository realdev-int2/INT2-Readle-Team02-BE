# 인프라 운영 정책

## 상태

이 문서는 backend repository가 소유한 배포/운영 계약만 정리한다. 전체 서비스 운영 절차나 런북은 이 backend docs migration 범위에 포함하지 않는다.

## 기준

- [백엔드 이미지 배포 계약](../DEPLOYMENT.md)
- [ADR-008](../adr/ADR-008.md)
- [ADR-012](../adr/ADR-012.md)
- [ADR-020](../adr/ADR-020.md)

## Backend runtime

- Backend image는 CI에서 빌드한 prebuilt image를 사용한다.
- 배포 입력은 immutable image digest와 matching 40-character Git SHA다.
- `latest` tag 또는 mutable tag는 배포 입력으로 사용하지 않는다.
- Runtime profile은 `prod`다.
- `prod` datasource와 S3 bucket 값은 환경 변수로 주입한다.
- Backend와 MySQL은 host port를 publish하지 않는다.
- MySQL은 application 배포 중 재생성하지 않는다.

## Network policy

단일 EC2 runtime에서는 public/private Podman network를 분리한다.

| Network | 용도 |
| --- | --- |
| `readle-public` | Nginx ingress와 frontend/backend upstream 통신 |
| `readle-private` | Backend, MySQL, Prometheus/exporter 내부 통신 |

Backend active/candidate는 public upstream과 private dependency 통신을 위해 두 network에 참여한다. Nginx는 public network만 사용한다.

## Backend Blue-Green

Backend 배포는 임시 Blue-Green 방식이다.

1. 현재 active slot을 확인한다.
2. candidate backend를 임시 기동한다.
3. candidate readiness endpoint를 확인한다.
4. Nginx upstream을 candidate로 전환한다.
5. reload 후 `/api/actuator/health/readiness`를 확인한다.
6. 실패하면 기존 active slot으로 되돌리고 candidate를 제거한다.
7. 성공하면 기존 active backend를 중지한다.

Active slot은 `readle-backend-blue` 또는 `readle-backend-green`이다.

## Actuator와 metrics

- `prod` profile은 management base path를 `/api/actuator`로 둔다.
- readiness는 `/api/actuator/health/readiness`에서 확인한다.
- Prometheus scrape endpoint는 `/api/actuator/prometheus`다.
- `security.prometheus-metrics.enabled=false` 기본값이면 Prometheus endpoint는 deny된다.
- Endpoint를 사용할 때는 `readle-monitor` Basic Auth user와 운영에서 주입한 password를 사용한다.
- Public Nginx boundary에서는 Prometheus endpoint를 외부 공개하지 않는다.

## Storage

- Local profile은 LocalStack S3 endpoint와 path-style access를 사용한다.
- Prod profile은 region `ap-northeast-2`와 환경 변수 `S3_BUCKET`을 사용한다.
- Prod profile에는 AWS access key/secret key를 설정하지 않는다. IAM role 또는 runtime credential chain을 사용한다.

## 비범위

다음 항목은 이 backend-local 정책 문서에 포함하지 않는다.

- Kubernetes, ECS, ALB, RDS, Redis 도입
- frontend 배포 절차
- 전체 EC2 런북
- 장애 대응 절차
- 운영 secret 값
- production host 이름
