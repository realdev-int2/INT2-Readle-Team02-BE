# 백엔드 배포 헬퍼

`deploy-backend.sh`는 단일 EC2 rootful Podman 스택에서 백엔드만 대상으로
임시 Blue-Green 배포를 수행합니다.

## 사전 조건

- `readle-nginx`와 백엔드 컨테이너를 소유한 EC2 호스트에서 실행합니다.
- `readle-nginx`, `readle-frontend`, 두 백엔드 slot은 `readle-public`에
  연결합니다. 백엔드 slot은 호스트 port를 publish하면 안 됩니다.
- 두 백엔드 slot과 MySQL은 `readle-private`에 연결합니다. `readle-nginx`는
  public default route만 갖도록 이 네트워크에 연결하지 않습니다.
- `READLE_BACKEND_ENV_FILE`로 재정의하지 않는 한 production 백엔드 env 파일은
  호스트의 `/etc/readle/backend.env`에 유지합니다.
- GHCR image repository prefix는 `/etc/readle/backend-image-repository`에 한 줄로
  관리합니다. 예시는 다음과 같습니다.

  ```text
  ghcr.io/<owner>/int2-readle-team02-be
  ```

## 최초 호스트 설치

CI가 호출하는 경로에 제공된 헬퍼를 한 번 설치합니다.

```sh
sudo install -d -o root -g root -m 0755 /usr/local/libexec/readle-backend
sudo install -o root -g root -m 0755 ./deploy-backend.sh /usr/local/libexec/readle-backend/deploy-backend
```

이 초기화 과정은 검토된 헬퍼를 root 소유·실행 가능 모드로 EC2 호스트에 복사합니다.
일반 배포에서는 호스트에서 `git pull`, Gradle build, image build를 수행하지 않습니다.
CI가 publish된 immutable image digest와 일치하는 Git revision을 설치된 헬퍼에
전달합니다.

## EC2 runtime 복구 검증

Reboot 복구 검증은 별도 oneshot unit인 `readle-runtime-verify.service`가 수행합니다.
백엔드 헬퍼는 backend slot topology 검증만 소유하고, host service 상태나 frontend,
MySQL 검증은 wrapper가 소유합니다.

최초 설치 순서는 다음만 허용합니다.

1. `/usr/local/libexec/readle-runtime-verify.candidate.<UTC>`에 candidate wrapper를
   stage합니다.
2. `sudo READLE_MYSQL_CONTAINER=readle-mysql /usr/local/libexec/readle-runtime-verify.candidate.<UTC> --preflight`를 실행합니다.
3. Preflight 통과 후 live backend helper, wrapper, forwarder, verifier unit의 기존 파일을
   timestamp backup합니다.
4. `deploy-backend`와 candidate를 `/usr/local/libexec` live 경로로 승격하고
   `/etc/systemd/system/readle-runtime-verify.service`를 설치합니다.
5. `systemctl daemon-reload` 후 `podman-restart.service`,
   `readle-podman-forward.service`, `readle-runtime-verify.service`를 enable합니다.
6. 즉시 검증이 필요할 때만 `systemctl start readle-runtime-verify.service`를 실행합니다.

표준 host의 MySQL 대상은 literal `readle-mysql`입니다. 이름이 다른 host만
`READLE_MYSQL_CONTAINER`를 명시하고, wrapper는 resolved target 하나가 실행 중이고
`readle-private` only이며 `restart=always`인지 확인합니다. MySQL container를 검색하지
않습니다.

`podman-restart.service`는 `/usr/lib/systemd/system/podman-restart.service` vendor
unit을 enable만 합니다. `/etc/systemd/system`으로 복사하거나 수정하지 않습니다.

수동 검증과 장애 진단은 다음 명령을 사용합니다.

```sh
sudo systemctl start readle-runtime-verify.service
sudo journalctl -u readle-runtime-verify.service -b --no-pager
```

Verifier가 실패해도 컨테이너 restart/remove, Nginx reload, deploy state 수정은 자동으로
수행하지 않습니다. 운영자는 journal의 실패 invariant를 확인한 뒤 기존 배포/복구
절차로 수동 처리합니다.

## Nginx upstream include 초기화

헬퍼는 호스트 측 include 파일 하나를 갱신하며, Nginx 컨테이너가 bind mount를 통해
같은 파일을 참조해야 합니다.

| 경로 | 기본값 |
| --- | --- |
| 호스트 include | `/opt/readle/nginx/readle-backend-upstream.conf` |
| 컨테이너 include | `/etc/nginx/readle-backend-upstream.conf` |

`ops/backend/nginx/backend-upstream.conf.template`로 호스트 파일을 초기화한 뒤
`readle-nginx`에 bind mount하고, Nginx config에서는 아래 컨테이너 경로를 정확히
include합니다.

```nginx
include /etc/nginx/readle-backend-upstream.conf;
```

파일 bind mount이므로 내용은 in-place로 갱신합니다. 호스트 파일을 `mv`로 교체하면
컨테이너 mount가 이전 inode를 계속 참조하므로 사용하면 안 됩니다.

`/var/lib/readle/backend-deploy.env`의 active slot은 이 include와 일치해야 합니다.

## Slot과 리소스 제한

헬퍼는 다음 두 개의 고정 백엔드 slot을 사용합니다.

- `readle-backend-blue`
- `readle-backend-green`

한 시점에는 하나의 slot만 트래픽을 처리합니다. 배포 중에는 비활성 slot이
candidate로 실행되며, 두 백엔드 slot 모두 `--memory=640m`으로 실행됩니다.

## 배포

이 디렉터리에서 immutable image digest와 예상 40자 Git revision을 전달합니다.

```sh
sudo ./deploy-backend.sh \
  ghcr.io/<owner>/int2-readle-team02-be@sha256:<64-lowercase-hex> \
  <40-lowercase-git-sha>
```

스크립트는 digest를 pull하고 image label `org.opencontainers.image.revision`을
확인합니다. Candidate를 `readle-public`에서 시작한 뒤 `readle-private`에 연결하고,
`/api/actuator/health/readiness`를 대기합니다. 이후 Nginx upstream include를 갱신하고
`nginx -t`, Nginx reload, edge를 통한 `/api/actuator/health/readiness` smoke check를
순서대로 수행합니다.

배포에는 다음 두 잠금을 사용합니다.

- `/run/lock/readle-candidate-deploy.lock`: 다른 candidate 방식 app 배포와의 동시
  실행을 막는 공용 candidate 잠금입니다.
- `/run/lock/readle-backend-deploy.lock`: 백엔드 배포 전용 잠금입니다.

Runtime verifier도 공용 candidate 잠금을 최대 30초 대기합니다. frontend 또는 backend
배포 중 수동 verifier 실행은 timeout으로 실패할 수 있으므로, 배포 완료 후 다시 실행합니다.

## Rollback 동작

Candidate readiness 또는 `nginx -t`가 실패하면 헬퍼는 candidate를 제거하고,
트래픽은 기존 active slot에 유지합니다.

Nginx reload 후 헬퍼는 edge smoke check 전에 pending rollback state를 기록합니다.
Smoke check가 실패하면 이전 upstream include를 복구하고 Nginx를 reload한 다음,
pending rollback state를 비우고 실패한 candidate를 제거합니다.

Cutover가 성공하면 헬퍼는 새 `last_good_*` state를 기록하고 이전 image tuple은
`previous_*`에 유지합니다. 이후 pending rollback state를 비우고 이전 slot을 중지합니다.
