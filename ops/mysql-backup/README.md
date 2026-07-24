# Readle MySQL S3 백업

이 디렉터리는 운영 EC2에서 `readle-mysql` 컨테이너를 S3로 백업하는 host helper와
systemd unit을 제공한다. 스크립트와 unit만 설치한다. MySQL 컨테이너, volume,
network, production port는 수정하지 않는다.

## 파일 경로

| 항목 | 운영 경로 |
| --- | --- |
| Helper | `/usr/local/libexec/readle-mysql-backup` |
| Service | `/etc/systemd/system/readle-mysql-backup.service` |
| Timer | `/etc/systemd/system/readle-mysql-backup.timer` |
| State/staging dir | `/var/lib/readle/mysql-backup` |
| State file | `/var/lib/readle/mysql-backup/readle-mysql-backup.state` |
| node_exporter textfile metric | `/var/lib/node_exporter/textfile_collector/readle_mysql_backup.prom` |

Helper는 staging dir 아래에 임시 `.sql.gz` 파일을 `0600`으로 만들고, 성공과 실패를
포함한 모든 종료 경로에서 삭제한다.

## 사전 조건

- EC2에는 rootful Podman, `gzip`, AWS CLI, systemd가 있어야 한다.
- MySQL 컨테이너 이름은 literal `readle-mysql`이어야 한다. 컨테이너를 검색하거나
  재생성하지 않는다.
- `/etc/readle/backend.env`는 root 소유 `0600`이어야 하며 다음 key를 포함해야 한다.
  Helper는 이 조건을 만족하지 않으면 source하지 않으며, 필수 key를 상속 환경 변수로
  보완하지 않는다. 값, 특히
  `MYSQL_ROOT_PASSWORD`, 를 출력하지 않는다.

```sh
sudo chown root:root /etc/readle/backend.env
sudo chmod 0600 /etc/readle/backend.env
sudo stat -c '%U %a %n' /etc/readle/backend.env
sudo grep -E '^(MYSQL_DATABASE|MYSQL_ROOT_PASSWORD|AWS_REGION|S3_BUCKET)=' /etc/readle/backend.env \
  | sed -E 's/=.*/=<set>/'
```

필수 key:

| Key | 용도 |
| --- | --- |
| `MYSQL_DATABASE` | `readle-mysql`에서 dump할 DB 이름 |
| `MYSQL_ROOT_PASSWORD` | `mysqldump` 인증. argv나 log에 출력하지 않는다. |
| `AWS_REGION` | S3 API region |
| `S3_BUCKET` | 업로드 대상 bucket. bucket discovery를 하지 않는다. |

`READLE_MYSQL_BACKUP_METRIC_FILE`로 node_exporter textfile metric 경로를 바꿀 수
있다. 경로는 절대 경로, `.prom` 파일명, root 소유의 기존 디렉터리 아래여야 한다.
Helper는 metric write 실패를 log에만 남기고 backup 성공/실패 판정을 바꾸지 않는다.
Monitoring installer는 기본 textfile collector directory
`/var/lib/node_exporter/textfile_collector`를 만들고 node_exporter에 read-only mount한다.

S3 go-live 전 infra owner가 다음을 확인해야 한다.

- 현재 configured `S3_BUCKET`/`AWS_REGION`을 사용한다. `ListBuckets`는 필요하지 않고
  사용하지 않는다.
- IAM/bucket policy는 `HeadBucket`, `PutObject`, `backups/mysql/*`에 대한 scoped
  `GetObject`를 허용해야 한다. `HeadObject` 검증은 이 `GetObject` 권한에 의존한다.
- 현재 EC2 role은 bucket lifecycle, versioning, PublicAccessBlock을 검사할 수 없다.
  Infra owner가 lifecycle 보관 기간 최소 7일, 권장 14일 이상을 확인한다.
- Versioned bucket이면 noncurrent object expiry도 확인한다.
- PublicAccessBlock이 켜져 있는지 확인한다.

MySQL exporter는 application/root credential을 재사용하지 않는다. Infra owner는
`backend/ops/monitoring/sql/mysqld-exporter-grants.sql.template`의 password placeholder를
운영 secret으로 바꿔 `readle-mysql`에 적용한 뒤, 같은 credential을
`/etc/readle/monitoring/mysqld-exporter.cnf`에 준비해야 한다. Monitoring installer는
exporter read access를 위해 파일을 `root:65534` 소유와 `0640` mode로 보정한다.

```sh
sudo install -d -o root -g root -m 0700 /etc/readle/monitoring
sudo install -o root -g 65534 -m 0640 backend/ops/monitoring/secrets/mysqld-exporter.cnf.example \
  /etc/readle/monitoring/mysqld-exporter.cnf
sudo stat -c '%U:%G %a %n' /etc/readle/monitoring/mysqld-exporter.cnf
```

Monitoring `install`은 credential file 존재를 요구하고 `root:65534` ownership과 `0640`
mode를 적용한다. SQL template은 전용 exporter 계정을 `MAX_USER_CONNECTIONS 3`으로
제한하고 `PROCESS`, `REPLICATION CLIENT`, `SELECT`만 부여한다.

## 설치

기존 운영 파일이 있으면 먼저 UTC timestamp backup을 남긴 뒤 교체한다.

```sh
TS="$(date -u +%Y%m%dT%H%M%SZ)"

sudo install -d -o root -g root -m 0755 /usr/local/libexec
sudo install -d -o root -g root -m 0700 /var/lib/readle/mysql-backup

for path in \
  /usr/local/libexec/readle-mysql-backup \
  /etc/systemd/system/readle-mysql-backup.service \
  /etc/systemd/system/readle-mysql-backup.timer
do
  if [ -e "$path" ]; then
    sudo cp -a "$path" "$path.$TS.bak"
  fi
done

sudo install -o root -g root -m 0755 backend/ops/mysql-backup/readle-mysql-backup \
  /usr/local/libexec/readle-mysql-backup
sudo install -o root -g root -m 0644 backend/ops/mysql-backup/readle-mysql-backup.service \
  /etc/systemd/system/readle-mysql-backup.service
sudo install -o root -g root -m 0644 backend/ops/mysql-backup/readle-mysql-backup.timer \
  /etc/systemd/system/readle-mysql-backup.timer
```

## systemd 등록과 검증

```sh
sudo systemd-analyze verify \
  /etc/systemd/system/readle-mysql-backup.service \
  /etc/systemd/system/readle-mysql-backup.timer
systemd-analyze calendar '*-*-* 00:30:00 UTC'

sudo systemctl daemon-reload
sudo systemctl enable --now readle-mysql-backup.timer
systemctl list-timers readle-mysql-backup.timer
systemctl status readle-mysql-backup.timer --no-pager
```

수동 1회 실행이 필요할 때만 service를 시작한다.

```sh
sudo systemctl start readle-mysql-backup.service
systemctl status readle-mysql-backup.service --no-pager
sudo journalctl -u readle-mysql-backup.service --utc --no-pager -n 100
```

## 성공 기준

성공은 다음 조건을 모두 만족할 때만 기록된다.

- `mysqldump`와 `gzip`이 성공한다.
- staged archive가 비어 있지 않고 `gzip -t`를 통과한다.
- S3 upload가 성공한다.
- `HeadObject`의 `ContentLength`가 local byte count와 정확히 일치한다.
- `/var/lib/readle/mysql-backup/readle-mysql-backup.state`에 `status=success`가
  `0600` state file로 기록된다.
- `/var/lib/node_exporter/textfile_collector/readle_mysql_backup.prom`에
  `readle_mysql_backup_success`와
  `readle_mysql_backup_last_success_timestamp_seconds`만 기록된다.
- staged `.sql.gz` 파일은 성공 후에도 남지 않는다.

Journal과 state file은 운영 증거일 뿐이다. Monitoring alert consumer 연결은 아직
별도 follow-up이며, journald만으로 alerting 완료를 주장하지 않는다.

## 장애 확인

```sh
systemctl status readle-mysql-backup.service --no-pager
sudo journalctl -u readle-mysql-backup.service --utc --no-pager -n 200
sudo sed -E 's/(MYSQL_ROOT_PASSWORD|password)=.*/\1=<redacted>/I' \
  /var/lib/readle/mysql-backup/readle-mysql-backup.state
sudo find /var/lib/readle/mysql-backup -maxdepth 1 -name '*.sql.gz' -ls
```

자주 보는 원인:

- `/etc/readle/backend.env`에 필수 key가 없거나 값 형식이 잘못됨.
- `readle-mysql` 컨테이너가 없거나 실행 중이 아님.
- S3 `HeadBucket`, `PutObject`, `backups/mysql/*` scoped `GetObject` 권한 누락.
- `HeadObject` byte count 불일치.
- Staging disk 부족.

## 설치 파일 롤백

롤백은 설치한 helper와 systemd unit 파일만 되돌린다. MySQL 컨테이너, volume,
network, S3 object는 수정하지 않는다.

```sh
TS=<backup-timestamp>

sudo install -o root -g root -m 0755 /usr/local/libexec/readle-mysql-backup.$TS.bak \
  /usr/local/libexec/readle-mysql-backup
sudo install -o root -g root -m 0644 /etc/systemd/system/readle-mysql-backup.service.$TS.bak \
  /etc/systemd/system/readle-mysql-backup.service
sudo install -o root -g root -m 0644 /etc/systemd/system/readle-mysql-backup.timer.$TS.bak \
  /etc/systemd/system/readle-mysql-backup.timer

sudo systemctl daemon-reload
sudo systemctl restart readle-mysql-backup.timer
systemctl status readle-mysql-backup.timer --no-pager
```

주간 복구 리허설은 [EC2 MySQL 백업 런북](../../../docs/runbook/ec2-mysql-backup-runbook.md)을
따른다.
