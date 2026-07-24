#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

fail() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

grep -R -nE '(^|[[:space:]])PublishPort=|--publish|Network=host' "$ROOT/quadlet" && fail "host port or host network exposure found"
grep -R -nE 'Image=[^@[:space:]]+:[^@[:space:]]+($|[[:space:]])|^[A-Z_]+_IMAGE=[^@[:space:]]+:[^@[:space:]]+($|[[:space:]])' "$ROOT/quadlet" "$ROOT/images.env" && fail "tagged image ref found"

for pair in \
  'prometheus.container:--memory=256m' \
  'grafana.container:--memory=512m' \
  'readle-node-exporter.container:--memory=32m' \
  'readle-mysqld-exporter.container:--memory=32m' \
  'readle-podman-exporter.container:--memory=64m'
do
  file="${pair%%:*}"
  value="${pair#*:}"
  grep -q -- "$value" "$ROOT/quadlet/$file" || fail "missing $value in $file"
done

grep -q 'scrape_interval: 30s' "$ROOT/config/prometheus/prometheus.yml" || fail "Prometheus scrape interval is not 30s"
grep -q 'evaluation_interval: 30s' "$ROOT/config/prometheus/prometheus.yml" || fail "Prometheus evaluation interval is not 30s"
grep -q 'refresh_interval: 30s' "$ROOT/config/prometheus/prometheus.yml" || fail "Prometheus file-SD refresh interval is not 30s"
grep -q 'timeInterval: 30s' "$ROOT/config/grafana/provisioning/datasources/prometheus.yml" || fail "Grafana Prometheus query interval is not 30s"
grep -q '"refresh": "30s"' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "Grafana dashboard refresh is not 30s"
grep -q 'storage.tsdb.retention.time=3d' "$ROOT/quadlet/prometheus.container" || fail "Prometheus retention time is not 3d"
grep -q 'storage.tsdb.retention.size=512MB' "$ROOT/quadlet/prometheus.container" || fail "Prometheus retention size is not 512MB"
grep -q 'password_file: /run/secrets/readle-backend-metrics-password' "$ROOT/config/prometheus/prometheus.yml" || fail "backend scrape does not use password_file"
grep -q "find \"\$root\" -type f -name '._\*' -delete" "$ROOT/readle-monitoring" || fail "monitoring installer does not purge macOS sidecar files from managed tree targets"
grep -q '\[\[ "$(basename "$file")" == ._\* \]\] && continue' "$ROOT/readle-monitoring" || fail "monitoring installer does not skip macOS sidecar files in source trees"
grep -q 'purge_macos_sidecars "$dst_root"' "$ROOT/readle-monitoring" || fail "monitoring installer does not clean stale macOS sidecar files after tree install"
grep -q 'BACKEND_METRICS_SECRET_OWNER="root:65534"' "$ROOT/readle-monitoring" || fail "backend metrics secret owner does not allow Prometheus uid 65534 gid 65534 read access"
grep -q 'BACKEND_METRICS_SECRET_MODE="640"' "$ROOT/readle-monitoring" || fail "backend metrics secret mode does not deny world access"
grep -q 'chown "$BACKEND_METRICS_SECRET_OWNER" "$path"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chown backend metrics secret"
grep -q 'chmod "$BACKEND_METRICS_SECRET_MODE" "$path"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chmod backend metrics secret"
grep -q '^  provision_backend_metrics_secret$' "$ROOT/readle-monitoring" || fail "monitoring installer does not provision backend metrics secret during install"
grep -q 'username: readle-monitor' "$ROOT/config/prometheus/prometheus.yml" || fail "backend scrape username is not readle-monitor"
grep -q '/etc/prometheus/file_sd/backend-active.json' "$ROOT/config/prometheus/prometheus.yml" || fail "backend file-SD path missing"
[[ -f "$ROOT/config/prometheus/file_sd/.gitkeep" ]] || fail "Prometheus file-SD mount target placeholder missing"
[[ ! -e "$ROOT/config/prometheus/file_sd/backend-active.json" ]] || fail "monitoring bundle must not ship an active backend file-SD target"
grep -q 'Volume=/var/lib/readle/monitoring/prometheus/file_sd:/etc/prometheus/file_sd:ro' "$ROOT/quadlet/prometheus.container" || fail "Prometheus file-SD host state mount missing"
grep -q 'Volume=/var/lib/readle/monitoring/prometheus:/prometheus' "$ROOT/quadlet/prometheus.container" || fail "Prometheus writable host state mount missing"
grep -q 'PROMETHEUS_STATE_OWNER="65534:65534"' "$ROOT/readle-monitoring" || fail "Prometheus state owner does not match pinned image uid:gid"
grep -q 'chown -R "$PROMETHEUS_STATE_OWNER" "$STATE_ROOT/prometheus"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chown Prometheus state"
[[ "$(grep -c '^  provision_prometheus_state$' "$ROOT/readle-monitoring")" -ge 2 ]] || fail "monitoring installer does not provision Prometheus state during install and self-test"
! grep -Eq '(^User=(0|root)(:|$)|--user[= ](0|root)(:|$))' "$ROOT/quadlet/prometheus.container" || fail "Prometheus root runtime override found"
grep -q 'Volume=/var/lib/readle/monitoring/grafana:/var/lib/grafana' "$ROOT/quadlet/grafana.container" || fail "Grafana writable host state mount missing"
grep -q 'GRAFANA_STATE_OWNER="472:0"' "$ROOT/readle-monitoring" || fail "Grafana state owner does not match pinned image uid:gid"
grep -q 'chown -R "$GRAFANA_STATE_OWNER" "$STATE_ROOT/grafana"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chown Grafana state"
[[ "$(grep -c '^  provision_grafana_state$' "$ROOT/readle-monitoring")" -ge 2 ]] || fail "monitoring installer does not provision Grafana state during install and self-test"
grep -q 'GRAFANA_ADMIN_SECRET_OWNER="root:root"' "$ROOT/readle-monitoring" || fail "Grafana admin secret owner does not allow root-group read access for Grafana uid 472 gid 0"
grep -q 'GRAFANA_ADMIN_SECRET_MODE="640"' "$ROOT/readle-monitoring" || fail "Grafana admin secret mode does not deny world access"
grep -q 'chown "$GRAFANA_ADMIN_SECRET_OWNER" "$path"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chown Grafana admin secret"
grep -q 'chmod "$GRAFANA_ADMIN_SECRET_MODE" "$path"' "$ROOT/readle-monitoring" || fail "monitoring installer does not chmod Grafana admin secret"
grep -q '^  provision_grafana_admin_secret$' "$ROOT/readle-monitoring" || fail "monitoring installer does not provision Grafana admin secret during install"
! grep -q 'systemctl enable --''now' "$ROOT/readle-monitoring" || fail "monitoring installer must not enable generated Quadlet units at start time"
grep -q 'systemctl start prometheus readle-node-exporter readle-mysqld-exporter readle-podman-exporter grafana' "$ROOT/readle-monitoring" || fail "monitoring installer must instruct systemctl start for generated Quadlet units"
grep -q 'root_url = %(protocol)s://%(domain)s/grafana/' "$ROOT/config/grafana/grafana.ini" || fail "Grafana root_url is not subpath"
grep -q 'serve_from_sub_path = true' "$ROOT/config/grafana/grafana.ini" || fail "Grafana serve_from_sub_path missing"
grep -q 'allow_sign_up = false' "$ROOT/config/grafana/grafana.ini" || fail "Grafana sign-up enabled"
grep -q 'enabled = false' "$ROOT/config/grafana/grafana.ini" || fail "Grafana anonymous auth not disabled"
grep -Fq 'http_server_requests_seconds_count{job=\"backend\"' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing backend HTTP request panel"
grep -Fq '"targets": [{"expr": "up{job=\"backend\"}", "instant": true}]' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "backend availability panel must use an instant query"
grep -Fq 'jvm_memory_used_bytes{job=\"backend\",area=\"heap\"}' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing JVM heap panel"
grep -Fq 'hikaricp_connections_active{job=\"backend\"}' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing DB pool panel"
grep -Fq 'node_cpu_seconds_total{job=\"node\",mode=\"idle\"}' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing host CPU panel"
grep -Fq 'node_filesystem_avail_bytes{job=\"node\",mountpoint=\"/\"' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing root disk panel"
grep -Fq 'sum by (name) (podman_container_mem_usage_bytes * on(id, instance, job) group_left(name) podman_container_info)' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard does not deduplicate container memory by name"
grep -Fq '"legendFormat": "{{name}}"' "$ROOT/config/grafana/dashboards/readle-overview.json" || fail "dashboard missing container name legend"
grep -Fxq 'location = /api/actuator/prometheus {' "$ROOT/nginx/prometheus-metrics-deny.conf.template" || fail "Nginx deny fragment is not an exact metrics location"
grep -Fxq '    deny all;' "$ROOT/nginx/prometheus-metrics-deny.conf.template" || fail "Nginx deny fragment missing deny"
grep -Fxq '    return 403;' "$ROOT/nginx/prometheus-metrics-deny.conf.template" || fail "Nginx deny fragment missing 403 status"
grep -q 'location = /grafana' "$ROOT/nginx/grafana-subpath.conf.template" || fail "Grafana exact /grafana route missing"
grep -q 'location \^~ /grafana/' "$ROOT/nginx/grafana-subpath.conf.template" || fail "Grafana prefix route is not ^~"
grep -q 'limit_req zone=readle_grafana_login' "$ROOT/nginx/grafana-subpath.conf.template" || fail "Grafana login rate-limit location missing"
grep -q 'limit_req_zone .*zone=readle_grafana_login:' "$ROOT/nginx/rate-limit-zones.conf.template" || fail "Grafana rate-limit zone template missing"
grep -q 'rate-limit-zones.conf.template.*rate-limit-zones.conf' "$ROOT/readle-monitoring" || fail "monitoring installer does not install the Grafana rate-limit zone"
grep -q 'CONTAINER_HOST=unix:///run/podman/podman.sock' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman exporter does not use Unix socket"
grep -q '^User=0:0$' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman exporter must run as root for the rootful Podman socket"
grep -q '^Environment=HOME=/tmp$' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman exporter HOME must be writable tmpfs"
grep -q '^Environment=XDG_CONFIG_HOME=/tmp$' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman exporter XDG config must be writable tmpfs"
grep -q 'Volume=/run/podman/podman.sock:/run/podman/podman.sock:ro' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman socket mount must stay read-only"
! grep -Eq '(^|[[:space:]])PublishPort=|--publish|Network=host' "$ROOT/quadlet/readle-podman-exporter.container" || fail "Podman exporter must not expose host ports or host networking"
grep -q -- '--pid=host' "$ROOT/quadlet/readle-node-exporter.container" || fail "node exporter host metrics preflight contract changed"
grep -q 'Volume=/var/lib/node_exporter/textfile_collector:/var/lib/node_exporter/textfile_collector:ro' "$ROOT/quadlet/readle-node-exporter.container" || fail "node exporter textfile collector mount missing"
grep -q -- '--collector.textfile.directory=/var/lib/node_exporter/textfile_collector' "$ROOT/quadlet/readle-node-exporter.container" || fail "node exporter textfile collector flag missing"
grep -q '^host=readle-mysql$' "$ROOT/secrets/mysqld-exporter.cnf.example" || fail "MySQL exporter example must target readle-mysql"
grep -q 'MYSQLD_EXPORTER_SECRET_OWNER="root:65534"' "$ROOT/readle-monitoring" || fail "MySQL exporter secret owner must allow only root plus exporter group"
grep -q 'MYSQLD_EXPORTER_SECRET_MODE="640"' "$ROOT/readle-monitoring" || fail "MySQL exporter secret mode must deny world access"
grep -q 'chown "$MYSQLD_EXPORTER_SECRET_OWNER" "$path"' "$ROOT/readle-monitoring" || fail "installer must chown MySQL exporter cnf for exporter group read access"
grep -q 'chmod "$MYSQLD_EXPORTER_SECRET_MODE" "$path"' "$ROOT/readle-monitoring" || fail "installer must chmod MySQL exporter cnf without world access"
grep -Fxq "CREATE USER IF NOT EXISTS 'readle_exporter'@'%' IDENTIFIED BY 'replace-with-exporter-password' WITH MAX_USER_CONNECTIONS 3;" "$ROOT/sql/mysqld-exporter-grants.sql.template" || fail "MySQL exporter grant template missing MySQL 8 connection cap syntax"
grep -Fxq "GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'readle_exporter'@'%';" "$ROOT/sql/mysqld-exporter-grants.sql.template" || fail "MySQL exporter global monitoring grants missing"
grep -Fxq "GRANT SELECT ON performance_schema.* TO 'readle_exporter'@'%';" "$ROOT/sql/mysqld-exporter-grants.sql.template" || fail "MySQL exporter performance schema select grant missing"
! grep -Fq "SELECT ON *.* TO 'readle_exporter'@'%';" "$ROOT/sql/mysqld-exporter-grants.sql.template" || fail "MySQL exporter must not receive global SELECT"
grep -q 'SystemMaxUse=256M' "$ROOT/systemd/journald-readle.conf.template" || fail "journald quota template missing bounded max use"
bash -n "$ROOT/readle-monitoring"
"$ROOT/readle-monitoring" self-test

printf 'monitoring bundle static validation passed\n'
