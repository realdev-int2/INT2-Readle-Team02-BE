#!/usr/bin/env bash
set -euo pipefail

IMAGE_REPOSITORY_FILE="${READLE_BACKEND_IMAGE_REPOSITORY_FILE:-/etc/readle/backend-image-repository}"
STATE_FILE="${READLE_BACKEND_STATE_FILE:-/var/lib/readle/backend-deploy.env}"
ENV_FILE="${READLE_BACKEND_ENV_FILE:-/etc/readle/backend.env}"
CANDIDATE_LOCK_FILE="${READLE_CANDIDATE_LOCK_FILE:-/run/lock/readle-candidate-deploy.lock}"
LOCK_FILE="${READLE_BACKEND_LOCK_FILE:-/run/lock/readle-backend-deploy.lock}"
NGINX="${READLE_NGINX_CONTAINER:-readle-nginx}"
NGINX_UPSTREAM_INCLUDE="${READLE_BACKEND_NGINX_INCLUDE:-/etc/nginx/readle-backend-upstream.conf}"
NGINX_UPSTREAM_INCLUDE_HOST="${READLE_BACKEND_NGINX_INCLUDE_HOST:-/opt/readle/nginx/readle-backend-upstream.conf}"
PUBLIC_NETWORK="${READLE_BACKEND_PUBLIC_NETWORK:-readle-public}"
PRIVATE_NETWORK="${READLE_BACKEND_PRIVATE_NETWORK:-readle-private}"
SLOT_A="readle-backend-blue"
SLOT_B="readle-backend-green"
READINESS_PATH="/api/actuator/health/readiness"
READINESS_URL="http://127.0.0.1:8080${READINESS_PATH}"
EDGE_URL="http://127.0.0.1${READINESS_PATH}"
MEMORY_LIMIT="640m"
DEPLOY_LABEL="io.readle.backend.image-ref"
HEALTH_WAIT_ATTEMPTS="${READLE_BACKEND_HEALTH_WAIT_ATTEMPTS:-45}"
EDGE_SMOKE_ATTEMPTS="${READLE_BACKEND_EDGE_SMOKE_ATTEMPTS:-5}"

log() { printf '%s\n' "$*" >&2; }
die() { log "error: $*"; exit 1; }

validate_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]]
}

validate_image_repository() {
  [[ "$1" =~ ^ghcr[.]io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*$ ]]
}

validate_image_id() {
  [[ "$1" =~ ^(sha256:)?[0-9a-f]{64}$ ]]
}

validate_slot() {
  [[ "$1" == "$SLOT_A" || "$1" == "$SLOT_B" ]]
}

inactive_slot() {
  [[ "$1" == "$SLOT_A" ]] && printf '%s\n' "$SLOT_B" || printf '%s\n' "$SLOT_A"
}

validate_nginx_include_path() {
  [[ "$1" =~ ^/[A-Za-z0-9._/-]+$ && "$1" != *"/../"* && "$1" != *"/.." ]]
}

load_image_prefix() {
  local prefix extra
  [[ -r "$IMAGE_REPOSITORY_FILE" ]] || return 1
  prefix="$(head -n 1 "$IMAGE_REPOSITORY_FILE")"
  extra="$(tail -n +2 "$IMAGE_REPOSITORY_FILE" | tr -d '[:space:]')"
  [[ -z "$extra" ]] || return 1
  validate_image_repository "$prefix" || return 1
  IMAGE_PREFIX="$prefix"
}

validate_image_ref() {
  [[ -n "${IMAGE_PREFIX:-}" && "$1" == "$IMAGE_PREFIX"@sha256:* ]] || return 1
  [[ "${1#"$IMAGE_PREFIX"@sha256:}" =~ ^[0-9a-f]{64}$ ]]
}

validate_tuple() {
  local image="$1" revision="$2" ref="$3" allow_empty="${4:-0}"
  if [[ -z "$image" && -z "$revision" && -z "$ref" && "$allow_empty" == "1" ]]; then
    return 0
  fi
  [[ -n "$image" && -n "$revision" && -n "$ref" ]] || return 1
  validate_image_id "$image" || return 1
  validate_sha "$revision" || return 1
  validate_image_ref "$ref"
}

reset_state_vars() {
  active_slot=""
  last_good_image=""
  last_good_revision=""
  last_good_ref=""
  previous_image=""
  previous_revision=""
  previous_ref=""
  pending_rollback_image=""
  pending_rollback_revision=""
  pending_rollback_ref=""
}

load_state() {
  local line key value
  local seen_active=0 seen_last_image=0 seen_last_revision=0 seen_last_ref=0
  local seen_previous_image=0 seen_previous_revision=0 seen_previous_ref=0
  local seen_pending_image=0 seen_pending_revision=0 seen_pending_ref=0

  reset_state_vars
  [[ -f "$STATE_FILE" ]] || return 1

  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    value="${line#*=}"
    [[ "$line" == *=* ]] || return 1
    case "$key" in
      active_slot)
        [[ "$seen_active" == 0 ]] || return 1
        validate_slot "$value" || return 1
        active_slot="$value"
        seen_active=1
        ;;
      last_good_image)
        [[ "$seen_last_image" == 0 ]] || return 1
        last_good_image="$value"
        seen_last_image=1
        ;;
      last_good_revision)
        [[ "$seen_last_revision" == 0 ]] || return 1
        last_good_revision="$value"
        seen_last_revision=1
        ;;
      last_good_ref)
        [[ "$seen_last_ref" == 0 ]] || return 1
        last_good_ref="$value"
        seen_last_ref=1
        ;;
      previous_image)
        [[ "$seen_previous_image" == 0 ]] || return 1
        previous_image="$value"
        seen_previous_image=1
        ;;
      previous_revision)
        [[ "$seen_previous_revision" == 0 ]] || return 1
        previous_revision="$value"
        seen_previous_revision=1
        ;;
      previous_ref)
        [[ "$seen_previous_ref" == 0 ]] || return 1
        previous_ref="$value"
        seen_previous_ref=1
        ;;
      pending_rollback_image)
        [[ "$seen_pending_image" == 0 ]] || return 1
        pending_rollback_image="$value"
        seen_pending_image=1
        ;;
      pending_rollback_revision)
        [[ "$seen_pending_revision" == 0 ]] || return 1
        pending_rollback_revision="$value"
        seen_pending_revision=1
        ;;
      pending_rollback_ref)
        [[ "$seen_pending_ref" == 0 ]] || return 1
        pending_rollback_ref="$value"
        seen_pending_ref=1
        ;;
      *) return 1 ;;
    esac
  done < "$STATE_FILE"

  [[ "$seen_active$seen_last_image$seen_last_revision$seen_last_ref" == "1111" ]] || return 1
  [[ "$seen_previous_image$seen_previous_revision$seen_previous_ref" == "111" ]] || return 1
  [[ "$seen_pending_image$seen_pending_revision$seen_pending_ref" == "111" ]] || return 1
  validate_tuple "$last_good_image" "$last_good_revision" "$last_good_ref" 0 || return 1
  validate_tuple "$previous_image" "$previous_revision" "$previous_ref" 1 || return 1
  validate_tuple "$pending_rollback_image" "$pending_rollback_revision" "$pending_rollback_ref" 1
}

save_state() {
  local state_dir temp
  state_dir="$(dirname "$STATE_FILE")"
  mkdir -p "$state_dir"
  temp="$(mktemp "$state_dir/.backend-deploy.env.XXXXXX")" || return 1
  if ! {
    printf 'active_slot=%s\n' "$active_slot"
    printf 'last_good_image=%s\n' "$last_good_image"
    printf 'last_good_revision=%s\n' "$last_good_revision"
    printf 'last_good_ref=%s\n' "$last_good_ref"
    printf 'previous_image=%s\n' "$previous_image"
    printf 'previous_revision=%s\n' "$previous_revision"
    printf 'previous_ref=%s\n' "$previous_ref"
    printf 'pending_rollback_image=%s\n' "$pending_rollback_image"
    printf 'pending_rollback_revision=%s\n' "$pending_rollback_revision"
    printf 'pending_rollback_ref=%s\n' "$pending_rollback_ref"
  } > "$temp"; then
    rm -f "$temp"
    return 1
  fi
  chmod 600 "$temp" || { rm -f "$temp"; return 1; }
  mv -f "$temp" "$STATE_FILE"
}

clear_pending() {
  pending_rollback_image=""
  pending_rollback_revision=""
  pending_rollback_ref=""
}

set_previous_from_last_good() {
  previous_image="$last_good_image"
  previous_revision="$last_good_revision"
  previous_ref="$last_good_ref"
}

podman_cmd() {
  8>&- 9>&- podman "$@"
}

container_exists() {
  podman_cmd container exists "$1"
}

container_image_id() {
  podman_cmd inspect "$1" --format '{{.Image}}' 2>/dev/null || true
}

container_deploy_ref() {
  podman_cmd inspect "$1" --format "{{ index .Config.Labels \"$DEPLOY_LABEL\" }}" 2>/dev/null || true
}

container_networks() {
  podman_cmd inspect "$1" --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' 2>/dev/null || true
}

container_attached_to_network() {
  [[ " $(container_networks "$1") " == *" $2 "* ]]
}

image_revision() {
  podman_cmd image inspect "$1" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}'
}

container_revision() {
  local image
  image="$(container_image_id "$1")"
  [[ -n "$image" ]] || return 1
  image_revision "$image"
}

memory_limit_bytes() {
  case "$MEMORY_LIMIT" in
    *m) printf '%s\n' "$((${MEMORY_LIMIT%m} * 1024 * 1024))" ;;
    *) return 1 ;;
  esac
}

container_memory_limit() {
  podman_cmd inspect "$1" --format '{{.HostConfig.Memory}}' 2>/dev/null || true
}

container_published_ports() {
  podman_cmd inspect "$1" --format '{{range $port, $bindings := .HostConfig.PortBindings}}{{if $bindings}}{{$port}} {{end}}{{end}}' 2>/dev/null || true
}

nginx_exec() {
  podman_cmd exec "$NGINX" "$@"
}

nginx_shell() {
  nginx_exec sh -c "$1"
}

nginx_include_line() {
  printf 'server %s:8080;\n' "$1"
}

read_nginx_include() {
  nginx_shell "test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE"
}

nginx_container_include_matches_host() {
  cmp -s "$NGINX_UPSTREAM_INCLUDE_HOST" <(read_nginx_include)
}

nginx_container_include_matches_slot() {
  nginx_include_line "$1" | cmp -s - <(read_nginx_include)
}

write_nginx_include() {
  nginx_include_line "$1" > "$NGINX_UPSTREAM_INCLUDE_HOST" || return 1
  chmod 644 "$NGINX_UPSTREAM_INCLUDE_HOST"
}

backup_nginx_include() {
  local include_dir backup
  include_dir="$(dirname "$NGINX_UPSTREAM_INCLUDE_HOST")"
  backup="$(mktemp "$include_dir/.readle-backend-upstream.conf.backup.XXXXXX")" || return 1
  cp -p "$NGINX_UPSTREAM_INCLUDE_HOST" "$backup" || { rm -f "$backup"; return 1; }
  printf '%s\n' "$backup"
}

restore_nginx_include() {
  cat "$1" > "$NGINX_UPSTREAM_INCLUDE_HOST" || return 1
  chmod 644 "$NGINX_UPSTREAM_INCLUDE_HOST"
}

nginx_references_include() {
  nginx_shell "grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1"
}

nginx_test() {
  nginx_exec nginx -t >/dev/null
}

nginx_reload() {
  nginx_exec nginx -s reload >/dev/null
}

edge_smoke() {
  # Edge TLS terminates locally; follow HTTP-to-HTTPS before accepting readiness.
  curl --max-time 5 --location --insecure -fsS "$EDGE_URL" >/dev/null
}

edge_smoke_retry() {
  for _ in $(seq 1 "$EDGE_SMOKE_ATTEMPTS"); do
    edge_smoke && return 0
    sleep 1
  done
  return 1
}

wait_healthy() {
  local name="$1" status
  for _ in $(seq 1 "$HEALTH_WAIT_ATTEMPTS"); do
    status="$(podman_cmd inspect "$name" --format '{{.State.Health.Status}}' 2>/dev/null || true)"
    [[ "$status" == "healthy" ]] && return 0
    sleep 2
  done
  return 1
}

candidate_readiness() {
  podman_cmd exec "$1" curl --fail --silent "$READINESS_URL" >/dev/null
}

candidate_preflight() {
  local slot="$1"
  wait_healthy "$slot" || { podman_cmd logs --tail=200 "$slot" >&2 || true; return 1; }
  candidate_readiness "$slot" || { podman_cmd logs --tail=200 "$slot" >&2 || true; return 1; }
}

remove_slot_if_present() {
  if container_exists "$1"; then
    podman_cmd rm -f "$1" >/dev/null
  fi
}

run_slot() {
  local slot="$1" image_ref="$2"
  podman_cmd run -d --restart=always --name "$slot" --network "$PUBLIC_NETWORK" \
    --env-file "$ENV_FILE" --memory="$MEMORY_LIMIT" \
    --label "$DEPLOY_LABEL=$image_ref" "$image_ref" >/dev/null || return 1
  if ! podman_cmd network connect "$PRIVATE_NETWORK" "$slot"; then
    podman_cmd rm -f "$slot" >/dev/null 2>&1 || true
    return 1
  fi
}

current_include_slot() {
  local line="$1"
  case "$line" in
    "server $SLOT_A:8080;") printf '%s\n' "$SLOT_A" ;;
    "server $SLOT_B:8080;") printf '%s\n' "$SLOT_B" ;;
    *) return 1 ;;
  esac
}

validate_bootstrap_preflight() {
  local include_line include_slot
  validate_nginx_include_path "$NGINX_UPSTREAM_INCLUDE" || die "invalid Nginx include path"
  validate_nginx_include_path "$NGINX_UPSTREAM_INCLUDE_HOST" || die "invalid host Nginx include path"
  [[ -f "$NGINX_UPSTREAM_INCLUDE_HOST" ]] || die "missing host Nginx upstream include: $NGINX_UPSTREAM_INCLUDE_HOST"
  [[ -r "$ENV_FILE" ]] || die "missing backend env file: $ENV_FILE"
  container_exists "$NGINX" || die "missing Nginx container: $NGINX"
  container_exists "$active_slot" || die "missing active backend slot: $active_slot"
  [[ -z "$(container_published_ports "$active_slot")" ]] || die "$active_slot must not publish host ports"
  [[ "$(container_memory_limit "$active_slot")" == "$(memory_limit_bytes)" ]] || die "$active_slot memory limit does not match $MEMORY_LIMIT"
  container_attached_to_network "$NGINX" "$PUBLIC_NETWORK" || die "$NGINX is not attached to $PUBLIC_NETWORK"
  ! container_attached_to_network "$NGINX" "$PRIVATE_NETWORK" || die "$NGINX must not attach to $PRIVATE_NETWORK"
  container_attached_to_network "$active_slot" "$PUBLIC_NETWORK" || die "$active_slot is not attached to $PUBLIC_NETWORK"
  container_attached_to_network "$active_slot" "$PRIVATE_NETWORK" || die "$active_slot is not attached to $PRIVATE_NETWORK"
  nginx_references_include || die "Nginx config does not reference $NGINX_UPSTREAM_INCLUDE"
  nginx_container_include_matches_host || die "host and container Nginx upstream includes differ"
  include_line="$(read_nginx_include)" || die "missing Nginx upstream include: $NGINX_UPSTREAM_INCLUDE"
  include_slot="$(current_include_slot "$include_line")" || die "Nginx upstream include has an unexpected target"
  [[ "$include_slot" == "$active_slot" ]] || die "state active_slot does not match Nginx upstream include"
  [[ "$(container_image_id "$active_slot")" == "$last_good_image" ]] || die "active container image does not match state"
  [[ "$(container_deploy_ref "$active_slot")" == "$last_good_ref" ]] || die "active container ref does not match state"
  [[ "$(container_revision "$active_slot" 2>/dev/null || true)" == "$last_good_revision" ]] || die "active container revision does not match state"
}

restore_include_or_die() {
  local include_backup="$1"
  restore_nginx_include "$include_backup" || die "failed to restore Nginx upstream include"
  nginx_test || die "restored Nginx include failed nginx -t"
  nginx_reload || die "failed to reload restored Nginx include"
}

rollback_after_flip() {
  local include_backup="$1" candidate_slot="$2"
  restore_include_or_die "$include_backup"
  rm -f "$include_backup"
  edge_smoke_retry || die "rollback edge smoke failed"
  clear_pending
  save_state || die "failed to save rollback state"
  podman_cmd rm -f "$candidate_slot" >/dev/null 2>&1 || true
}

deploy() {
  local image_ref="$1" expected_sha="$2" candidate_slot old_slot include_backup new_image
  local old_active_slot old_last_good_image old_last_good_revision old_last_good_ref
  local old_previous_image old_previous_revision old_previous_ref

  load_image_prefix || die "missing or invalid image repository file: $IMAGE_REPOSITORY_FILE"
  validate_image_ref "$image_ref" || die "image must be ${IMAGE_PREFIX}@sha256:<64 lowercase hex>"
  validate_sha "$expected_sha" || die "expected SHA must be 40 lowercase hex characters"

  exec 8>"$CANDIDATE_LOCK_FILE"
  flock -w 120 -x 8 || die "timed out waiting 120 seconds for candidate deployment lock"
  exec 9>"$LOCK_FILE"
  flock -w 120 -x 9 || die "timed out waiting 120 seconds for backend deployment lock"

  load_state || die "failed to load deployment state"
  validate_bootstrap_preflight
  candidate_slot="$(inactive_slot "$active_slot")"
  old_slot="$active_slot"
  old_active_slot="$active_slot"
  old_last_good_image="$last_good_image"
  old_last_good_revision="$last_good_revision"
  old_last_good_ref="$last_good_ref"
  old_previous_image="$previous_image"
  old_previous_revision="$previous_revision"
  old_previous_ref="$previous_ref"

  if [[ "$last_good_ref" == "$image_ref" && "$last_good_revision" == "$expected_sha" ]] && edge_smoke; then
    log "requested revision already live and healthy"
    return
  fi

  podman_cmd pull "$image_ref" >/dev/null
  [[ "$(image_revision "$image_ref")" == "$expected_sha" ]] || die "image revision label does not match expected SHA"

  remove_slot_if_present "$candidate_slot"
  run_slot "$candidate_slot" "$image_ref"
  if ! candidate_preflight "$candidate_slot"; then
    podman_cmd rm -f "$candidate_slot" >/dev/null 2>&1 || true
    die "candidate did not become ready"
  fi

  include_backup="$(backup_nginx_include)" || die "failed to backup Nginx upstream include"
  if ! write_nginx_include "$candidate_slot" || ! nginx_container_include_matches_slot "$candidate_slot" || ! nginx_test; then
    restore_nginx_include "$include_backup" || true
    rm -f "$include_backup"
    podman_cmd rm -f "$candidate_slot" >/dev/null 2>&1 || true
    die "candidate Nginx config failed"
  fi

  pending_rollback_image="$last_good_image"
  pending_rollback_revision="$last_good_revision"
  pending_rollback_ref="$last_good_ref"
  if ! save_state; then
    restore_nginx_include "$include_backup" || true
    rm -f "$include_backup"
    podman_cmd rm -f "$candidate_slot" >/dev/null 2>&1 || true
    die "failed to save pending rollback state"
  fi

  if ! nginx_reload || ! edge_smoke_retry; then
    rollback_after_flip "$include_backup" "$candidate_slot"
    die "post-cutover smoke failed; rolled back"
  fi

  new_image="$(container_image_id "$candidate_slot")"
  validate_image_id "$new_image" || die "deployed container image ID is invalid"
  set_previous_from_last_good
  active_slot="$candidate_slot"
  last_good_image="$new_image"
  last_good_revision="$expected_sha"
  last_good_ref="$image_ref"
  clear_pending
  if ! save_state; then
    restore_include_or_die "$include_backup"
    rm -f "$include_backup"
    edge_smoke_retry || die "post-save rollback edge smoke failed"
    podman_cmd rm -f "$candidate_slot" >/dev/null 2>&1 || true
    active_slot="$old_active_slot"
    last_good_image="$old_last_good_image"
    last_good_revision="$old_last_good_revision"
    last_good_ref="$old_last_good_ref"
    previous_image="$old_previous_image"
    previous_revision="$old_previous_revision"
    previous_ref="$old_previous_ref"
    clear_pending
    save_state || true
    die "failed to save deployment state; rolled back traffic"
  fi
  rm -f "$include_backup"
  podman_cmd stop "$old_slot" >/dev/null 2>&1 || true
  log "deployed $expected_sha to $active_slot"
}

self_test() {
  local original_repository_file original_state original_env original_lock original_candidate_lock original_include original_include_host original_wait original_edge
  local repository_file state_file env_file state_dir good_sha good_ref good_image old_image
  local log_file include_file include_backup save_count_file

  original_repository_file="$IMAGE_REPOSITORY_FILE"
  original_state="$STATE_FILE"
  original_env="$ENV_FILE"
  original_candidate_lock="$CANDIDATE_LOCK_FILE"
  original_lock="$LOCK_FILE"
  original_include="$NGINX_UPSTREAM_INCLUDE"
  original_include_host="$NGINX_UPSTREAM_INCLUDE_HOST"
  original_wait="$HEALTH_WAIT_ATTEMPTS"
  original_edge="$EDGE_SMOKE_ATTEMPTS"

  state_dir="$(mktemp -d)"
  repository_file="$state_dir/repository"
  state_file="$state_dir/state.env"
  env_file="$state_dir/backend.env"
  include_file="$state_dir/backend-upstream.conf"
  printf '%s\n' 'ghcr.io/example-org/int2-readle-team02-be' > "$repository_file"
  : > "$env_file"
  IMAGE_REPOSITORY_FILE="$repository_file"
  STATE_FILE="$state_file"
  ENV_FILE="$env_file"
  CANDIDATE_LOCK_FILE="$state_dir/candidate.lock"
  LOCK_FILE="$state_dir/deploy.lock"
  NGINX_UPSTREAM_INCLUDE="$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  HEALTH_WAIT_ATTEMPTS=1
  EDGE_SMOKE_ATTEMPTS=1
  load_image_prefix

  good_sha="0123456789abcdef0123456789abcdef01234567"
  good_ref="${IMAGE_PREFIX}@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  good_image="sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  old_image="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

  validate_sha "$good_sha"
  ! validate_sha "0123"
  validate_image_ref "$good_ref"
  ! validate_image_ref "${IMAGE_PREFIX}:main"
  ! validate_image_ref "ghcr.io/other/int2-readle-team02-be@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  [[ "$(inactive_slot "$SLOT_A")" == "$SLOT_B" ]]
  [[ "$(inactive_slot "$SLOT_B")" == "$SLOT_A" ]]

  active_slot="$SLOT_A"
  last_good_image="$old_image"
  last_good_revision="$good_sha"
  last_good_ref="$good_ref"
  previous_image=""
  previous_revision=""
  previous_ref=""
  clear_pending
  save_state
  load_state
  [[ "$active_slot" == "$SLOT_A" && "$last_good_image" == "$old_image" ]]
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    "previous_image=$good_image" \
    'previous_revision=' \
    "previous_ref=$good_ref" \
    'pending_rollback_image=' \
    'pending_rollback_revision=' \
    'pending_rollback_ref=' > "$STATE_FILE"
  ! load_state
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    'previous_image=' \
    'previous_revision=' \
    'previous_ref=' \
    "pending_rollback_image=$good_image" \
    "pending_rollback_revision=$good_sha" \
    'pending_rollback_ref=' > "$STATE_FILE"
  ! load_state

  podman_cmd() {
    printf '%s\n' "$*" >> "$log_file"
    case "$*" in
      "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
      "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
      *"HostConfig.PortBindings"*) return 0 ;;
      "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
      "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
      "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
      "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") nginx_include_line "$SLOT_A"; return 0 ;;
      *"--format {{.State.Health.Status}}"*) printf '%s\n' healthy; return 0 ;;
      "image inspect $old_image --format "*) printf '%s\n' "$good_sha"; return 0 ;;
      *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
    esac
  }
  container_deploy_ref() { printf '%s\n' "$good_ref"; }
  container_revision() { printf '%s\n' "$good_sha"; }
  log_file="$(mktemp)"
  printf '%s\n' "server $SLOT_A:8080;" > "$NGINX_UPSTREAM_INCLUDE_HOST"
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    'previous_image=' \
    'previous_revision=' \
    'previous_ref=' \
    'pending_rollback_image=' \
    'pending_rollback_revision=' \
    'pending_rollback_ref=' > "$STATE_FILE"
  load_state
  validate_bootstrap_preflight
  ! (
    container_networks() {
      printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"
    }
    validate_bootstrap_preflight
  ) 2>/dev/null
  ! grep -q ' rm -f ' "$log_file"
  log_file="$(mktemp)"
  ! (
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        *"HostConfig.PortBindings"*) return 0 ;;
        "inspect $SLOT_A --format {{.HostConfig.Memory}}") printf '%s\n' 536870912; return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    validate_bootstrap_preflight
  ) 2>/dev/null
  ! grep -q 'rm -f\|exec readle-nginx\|{{.Image}}' "$log_file"
  wait_healthy "$SLOT_B"
  grep -q "inspect $SLOT_B --format {{.State.Health.Status}}" "$log_file"
  printf '%s\n' "server $SLOT_B:8080;" > "$NGINX_UPSTREAM_INCLUDE_HOST"
  ! ( validate_bootstrap_preflight ) 2>/dev/null
  printf '%s\n' "server $SLOT_A:8080;" > "$NGINX_UPSTREAM_INCLUDE_HOST"

  log_file="$(mktemp)"
  ! (
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        *"HostConfig.PortBindings"*) printf '%s\n' '0.0.0.0:8080'; return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    validate_bootstrap_preflight
  ) 2>/dev/null
  ! grep -q 'HostConfig.Memory\|exec readle-nginx\|{{.Image}}\|rm -f' "$log_file"

  include_file="$state_dir/backend-upstream-no-backup.conf"
  printf '%s\n' "server $SLOT_A:8080;" > "$include_file"
  NGINX_UPSTREAM_INCLUDE="$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    'previous_image=' \
    'previous_revision=' \
    'previous_ref=' \
    'pending_rollback_image=' \
    'pending_rollback_revision=' \
    'pending_rollback_ref=' > "$STATE_FILE"

  log_file="$(mktemp)"
  podman_cmd() {
    printf '%s\n' "$*" >> "$log_file"
    case "$*" in
      "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
      "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
      *"HostConfig.PortBindings"*) return 0 ;;
      "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
      "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
      "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
      "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") cat "$NGINX_UPSTREAM_INCLUDE_HOST"; return 0 ;;
      *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
    esac
  }
  edge_smoke() { return 0; }
  flock() { return 0; }
  deploy "$good_ref" "$good_sha"
  ! compgen -G "$state_dir/.readle-backend-upstream.conf.backup.*" >/dev/null
  ! grep -q "^pull " "$log_file"

  log_file="$(mktemp)"
  ! (
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
        *"HostConfig.PortBindings"*) return 0 ;;
        "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
        "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
        "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
        "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") cat "$NGINX_UPSTREAM_INCLUDE_HOST"; return 0 ;;
        "pull $good_ref") return 0 ;;
        "image inspect $good_ref --format "*) printf '%s\n' 1111111111111111111111111111111111111111; return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    edge_smoke() { return 1; }
    flock() { return 0; }
    deploy "$good_ref" "$good_sha"
  ) 2>/dev/null
  ! compgen -G "$state_dir/.readle-backend-upstream.conf.backup.*" >/dev/null

  log_file="$(mktemp)"
  ! (
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        "container exists $SLOT_B") return 1 ;;
        "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
        *"HostConfig.PortBindings"*) return 0 ;;
        "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
        "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
        "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
        "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") cat "$NGINX_UPSTREAM_INCLUDE_HOST"; return 0 ;;
        "pull $good_ref") return 0 ;;
        "image inspect $good_ref --format "*) printf '%s\n' "$good_sha"; return 0 ;;
        "run -d --restart=always --name $SLOT_B --network $PUBLIC_NETWORK --env-file $ENV_FILE --memory=$MEMORY_LIMIT --label $DEPLOY_LABEL=$good_ref $good_ref") return 0 ;;
        "network connect $PRIVATE_NETWORK $SLOT_B") return 0 ;;
        "inspect $SLOT_B --format {{.State.Health.Status}}") printf '%s\n' unhealthy; return 0 ;;
        "logs --tail=200 $SLOT_B") return 0 ;;
        "rm -f $SLOT_B") return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    edge_smoke() { return 1; }
    flock() { return 0; }
    deploy "$good_ref" "$good_sha"
  ) 2>/dev/null
  ! compgen -G "$state_dir/.readle-backend-upstream.conf.backup.*" >/dev/null

  log_file="$(mktemp)"
  include_file="$state_dir/backend-upstream-host.conf"
  printf '%s\n' "server $SLOT_A:8080;" > "$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  podman_cmd() {
    printf '%s\n' "$*" >> "$log_file"
    case "$*" in
      "exec $NGINX nginx -t"|"exec $NGINX nginx -s reload") return 0 ;;
      "rm -f $SLOT_B") return 0 ;;
      *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
    esac
  }
  edge_smoke_retry() { return 0; }
  active_slot="$SLOT_A"
  last_good_image="$old_image"
  last_good_revision="$good_sha"
  last_good_ref="$good_ref"
  pending_rollback_image="$old_image"
  pending_rollback_revision="$good_sha"
  pending_rollback_ref="$good_ref"
  include_backup="$(backup_nginx_include)"
  printf '%s\n' "server $SLOT_B:8080;" > "$NGINX_UPSTREAM_INCLUDE_HOST"
  rollback_after_flip "$include_backup" "$SLOT_B"
  ! grep -q "cat > $NGINX_UPSTREAM_INCLUDE" "$log_file"
  [[ "$(cat "$NGINX_UPSTREAM_INCLUDE_HOST")" == "$(nginx_include_line "$SLOT_A")" ]]
  [[ -z "$pending_rollback_image" && -z "$pending_rollback_revision" && -z "$pending_rollback_ref" ]]
  load_state
  [[ -z "$pending_rollback_image" ]]

  log_file="$(mktemp)"
  include_file="$state_dir/backend-upstream-mismatch.conf"
  printf '%s\n' "server $SLOT_A:8080;" > "$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  podman_cmd() {
    printf '%s\n' "$*" >> "$log_file"
    case "$*" in
      "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") nginx_include_line "$SLOT_A"; return 0 ;;
      "exec $NGINX nginx -t") return 0 ;;
      "rm -f $SLOT_B") return 0 ;;
      *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
    esac
  }
  include_backup="$(backup_nginx_include)"
  if ! write_nginx_include "$SLOT_B" || ! nginx_container_include_matches_slot "$SLOT_B" || ! nginx_test; then
    restore_nginx_include "$include_backup" || true
    rm -f "$include_backup"
    podman_cmd rm -f "$SLOT_B" >/dev/null 2>&1 || true
  fi
  [[ "$(cat "$NGINX_UPSTREAM_INCLUDE_HOST")" == "$(nginx_include_line "$SLOT_A")" ]]
  ! grep -q "exec $NGINX nginx -t" "$log_file"

  log_file="$(mktemp)"
  save_count_file="$(mktemp)"
  printf '%s\n' 0 > "$save_count_file"
  include_file="$state_dir/backend-upstream-final-save.conf"
  printf '%s\n' "server $SLOT_A:8080;" > "$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    "previous_image=$good_image" \
    "previous_revision=$good_sha" \
    "previous_ref=$good_ref" \
    'pending_rollback_image=' \
    'pending_rollback_revision=' \
    'pending_rollback_ref=' > "$STATE_FILE"
  ! (
    save_state() {
      local count
      count="$(cat "$save_count_file")"
      count="$((count + 1))"
      printf '%s\n' "$count" > "$save_count_file"
      printf 'save_state %s\n' "$count" >> "$log_file"
      [[ "$count" != 2 ]] || return 1
      printf '%s\n' \
        "active_slot=$active_slot" \
        "last_good_image=$last_good_image" \
        "last_good_revision=$last_good_revision" \
        "last_good_ref=$last_good_ref" \
        "previous_image=$previous_image" \
        "previous_revision=$previous_revision" \
        "previous_ref=$previous_ref" \
        "pending_rollback_image=$pending_rollback_image" \
        "pending_rollback_revision=$pending_rollback_revision" \
        "pending_rollback_ref=$pending_rollback_ref" > "$STATE_FILE"
    }
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        "container exists $SLOT_B") return 1 ;;
        "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
        "inspect $SLOT_B --format {{.Image}}") printf '%s\n' "$good_image"; return 0 ;;
        *"HostConfig.PortBindings"*) return 0 ;;
        "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
        "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
        "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
        "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") cat "$NGINX_UPSTREAM_INCLUDE_HOST"; return 0 ;;
        "pull $good_ref") return 0 ;;
        "image inspect $good_ref --format "*) printf '%s\n' "$good_sha"; return 0 ;;
        "run -d --restart=always --name $SLOT_B --network $PUBLIC_NETWORK --env-file $ENV_FILE --memory=$MEMORY_LIMIT --label $DEPLOY_LABEL=$good_ref $good_ref") return 0 ;;
        "network connect $PRIVATE_NETWORK $SLOT_B") return 0 ;;
        "inspect $SLOT_B --format {{.State.Health.Status}}") printf '%s\n' healthy; return 0 ;;
        "exec $SLOT_B curl --fail --silent $READINESS_URL") return 0 ;;
        "exec $NGINX nginx -t"|"exec $NGINX nginx -s reload") return 0 ;;
        "rm -f $SLOT_B") return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    edge_smoke() { return 1; }
    edge_smoke_retry() { printf '%s\n' edge_smoke_retry >> "$log_file"; return 0; }
    flock() { return 0; }
    deploy "$good_ref" "$good_sha"
  ) 2>/dev/null
  [[ "$(cat "$NGINX_UPSTREAM_INCLUDE_HOST")" == "$(nginx_include_line "$SLOT_A")" ]]
  grep -q 'save_state 3' "$log_file"
  [[ "$(grep -c "exec $NGINX nginx -s reload" "$log_file")" == 2 ]]
  grep -q '^edge_smoke_retry$' "$log_file"
  grep -q "rm -f $SLOT_B" "$log_file"
  load_state
  [[ "$active_slot" == "$SLOT_A" && "$last_good_image" == "$old_image" ]]
  [[ "$previous_image" == "$good_image" && "$previous_revision" == "$good_sha" && "$previous_ref" == "$good_ref" ]]
  [[ -z "$pending_rollback_image" && -z "$pending_rollback_revision" && -z "$pending_rollback_ref" ]]

  log_file="$(mktemp)"
  include_file="$state_dir/backend-upstream-pending-save.conf"
  printf '%s\n' "server $SLOT_A:8080;" > "$include_file"
  NGINX_UPSTREAM_INCLUDE_HOST="$include_file"
  printf '%s\n' \
    "active_slot=$SLOT_A" \
    "last_good_image=$old_image" \
    "last_good_revision=$good_sha" \
    "last_good_ref=$good_ref" \
    'previous_image=' \
    'previous_revision=' \
    'previous_ref=' \
    'pending_rollback_image=' \
    'pending_rollback_revision=' \
    'pending_rollback_ref=' > "$STATE_FILE"
  ! (
    save_state() {
      printf '%s\n' save_state >> "$log_file"
      return 1
    }
    podman_cmd() {
      printf '%s\n' "$*" >> "$log_file"
      case "$*" in
        "container exists $NGINX"|"container exists $SLOT_A") return 0 ;;
        "container exists $SLOT_B") return 1 ;;
        "inspect $SLOT_A --format {{.Image}}") printf '%s\n' "$old_image"; return 0 ;;
        *"HostConfig.PortBindings"*) return 0 ;;
        "inspect $SLOT_A --format {{.HostConfig.Memory}}") memory_limit_bytes; return 0 ;;
        "inspect $NGINX --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK"; return 0 ;;
      "inspect $SLOT_A --format "*NetworkSettings.Networks*) printf '%s\n' "$PUBLIC_NETWORK $PRIVATE_NETWORK"; return 0 ;;
        "exec $NGINX sh -c grep -R -F 'include $NGINX_UPSTREAM_INCLUDE;' /etc/nginx >/dev/null 2>&1") return 0 ;;
        "exec $NGINX sh -c test -f $NGINX_UPSTREAM_INCLUDE && cat $NGINX_UPSTREAM_INCLUDE") cat "$NGINX_UPSTREAM_INCLUDE_HOST"; return 0 ;;
        "pull $good_ref") return 0 ;;
        "image inspect $good_ref --format "*) printf '%s\n' "$good_sha"; return 0 ;;
        "run -d --restart=always --name $SLOT_B --network $PUBLIC_NETWORK --env-file $ENV_FILE --memory=$MEMORY_LIMIT --label $DEPLOY_LABEL=$good_ref $good_ref") return 0 ;;
        "network connect $PRIVATE_NETWORK $SLOT_B") return 0 ;;
        "inspect $SLOT_B --format {{.State.Health.Status}}") printf '%s\n' healthy; return 0 ;;
        "exec $SLOT_B curl --fail --silent $READINESS_URL") return 0 ;;
        "exec $NGINX nginx -t") return 0 ;;
        "rm -f $SLOT_B") return 0 ;;
        *) printf 'unexpected podman command: %s\n' "$*" >&2; return 1 ;;
      esac
    }
    edge_smoke() { return 1; }
    flock() { return 0; }
    deploy "$good_ref" "$good_sha"
  ) 2>/dev/null
  [[ "$(cat "$NGINX_UPSTREAM_INCLUDE_HOST")" == "$(nginx_include_line "$SLOT_A")" ]]
  grep -q '^save_state$' "$log_file"
  grep -q "rm -f $SLOT_B" "$log_file"
  ! grep -q "exec $NGINX nginx -s reload" "$log_file"
  ! compgen -G "$state_dir/.readle-backend-upstream.conf.backup.*" >/dev/null

  rm -rf "$state_dir"
  IMAGE_REPOSITORY_FILE="$original_repository_file"
  STATE_FILE="$original_state"
  ENV_FILE="$original_env"
  CANDIDATE_LOCK_FILE="$original_candidate_lock"
  LOCK_FILE="$original_lock"
  NGINX_UPSTREAM_INCLUDE="$original_include"
  NGINX_UPSTREAM_INCLUDE_HOST="$original_include_host"
  HEALTH_WAIT_ATTEMPTS="$original_wait"
  EDGE_SMOKE_ATTEMPTS="$original_edge"
  log "self-test passed"
}

main() {
  [[ "${1:-}" != "--self-test" ]] || { self_test; return; }
  [[ "$#" == 2 ]] || die "usage: $0 <image@sha256:digest> <40-char-git-sha>"
  deploy "$1" "$2"
}

main "$@"
