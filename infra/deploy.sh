#!/usr/bin/env bash
set -Eeuo pipefail

SHA="${1:?Commit SHA is required}"
IMAGE_BASE="${2:?Image base is required}"
BUILD_BACKEND="${3:?Backend flag is required}"
BUILD_FRONTEND="${4:?Frontend flag is required}"
EXPECTED_BASE="${5:?Expected deployment baseline is required}"

[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "$EXPECTED_BASE" = NONE || "$EXPECTED_BASE" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ "$IMAGE_BASE" =~ ^ghcr\.io/[a-z0-9._/-]+$ ]] || exit 1
[[ "$BUILD_BACKEND" = true || "$BUILD_BACKEND" = false ]] || exit 1
[[ "$BUILD_FRONTEND" = true || "$BUILD_FRONTEND" = false ]] || exit 1

RELEASE_DIR="/home/ubuntu/unnie/releases/$SHA"
STATE_DIR="/home/ubuntu/unnie/deploy-state"
ENV_FILE="/home/ubuntu/unnie/infra/.env"

mkdir -p "$STATE_DIR"
test -f "$ENV_FILE"

# 서버에서 배포가 동시에 실행되지 않도록 잠근다.
exec 9>"$STATE_DIR/deploy.lock"

flock -n 9 || {
  echo "Another deployment is running"
  exit 1
}

LAST=NONE

if [ -f "$STATE_DIR/last-successful-sha" ]; then
  LAST=$(cat "$STATE_DIR/last-successful-sha")
fi

# 이미지 빌드 중 다른 배포가 진행됐다면 중단한다.
if [ "$LAST" != "$EXPECTED_BASE" ]; then
  echo "Deployment baseline changed while building; rerun the workflow"
  exit 1
fi

current_image() {
  local service="$1"
  local container key record value

  # 중지된 컨테이너도 포함한다.
  container=$(sudo docker ps -aq \
    --filter "label=com.docker.compose.project=unnie-app" \
    --filter "label=com.docker.compose.service=$service")

  if [ -n "$container" ]; then
    if [[ "$container" == *$'\n'* ]]; then
      echo "Multiple containers found for $service" >&2
      return 1
    fi

    sudo docker inspect \
      --format '{{.Config.Image}}' "$container"
    return
  fi

  # 컨테이너가 없으면 마지막 성공 배포 기록을 사용한다.
  if [ "$LAST" != NONE ]; then
    record="$STATE_DIR/images-$LAST.env"
    key="${service^^}_IMAGE"

    if [ -f "$record" ]; then
      value=$(sed -n "s/^${key}=//p" "$record")

      if [ -n "$value" ]; then
        printf '%s\n' "$value"
        return
      fi
    fi
  fi

  return 0
}

PREVIOUS_BACKEND_IMAGE=$(current_image backend)
PREVIOUS_FRONTEND_IMAGE=$(current_image frontend)

# 성공 배포 기록이 있으면 실행 중 컨테이너보다 그 기록을 우선한다.
# 이전 배포 실패로 컨테이너만 교체된 경우에도 정상 버전을 유지한다.
if [ "$LAST" != NONE ] && [ -f "$STATE_DIR/images-$LAST.env" ]; then
  record="$STATE_DIR/images-$LAST.env"

  saved_backend=$(sed -n 's/^BACKEND_IMAGE=//p' "$record")
  saved_frontend=$(sed -n 's/^FRONTEND_IMAGE=//p' "$record")

  PREVIOUS_BACKEND_IMAGE="${saved_backend:-$PREVIOUS_BACKEND_IMAGE}"
  PREVIOUS_FRONTEND_IMAGE="${saved_frontend:-$PREVIOUS_FRONTEND_IMAGE}"
fi

BACKEND_IMAGE="$PREVIOUS_BACKEND_IMAGE"
FRONTEND_IMAGE="$PREVIOUS_FRONTEND_IMAGE"

services=()

if [ "$BUILD_BACKEND" = true ]; then
  BACKEND_IMAGE="$IMAGE_BASE-backend:$SHA"
  services+=(backend)
fi

if [ "$BUILD_FRONTEND" = true ]; then
  FRONTEND_IMAGE="$IMAGE_BASE-frontend:$SHA"
  services+=(frontend)
fi

compose() {
  sudo env \
    BACKEND_IMAGE="$BACKEND_IMAGE" \
    FRONTEND_IMAGE="$FRONTEND_IMAGE" \
    docker compose \
    -p unnie-app \
    --env-file "$ENV_FILE" \
    -f "$RELEASE_DIR/compose.app.yml" "$@"
}

handle_deploy_failure() {
  local failure_code="${1:-1}"
  local rollback_ready=false
  local image attempt

  # 롤백 중 오류로 실패 처리 함수가 다시 호출되는 것을 방지한다.
  trap - ERR
  set +e

  echo "Deployment failed; restoring previous images"

  compose ps
  compose logs --tail=80 backend frontend

  if [ -z "$PREVIOUS_BACKEND_IMAGE" ] \
    || [ -z "$PREVIOUS_FRONTEND_IMAGE" ]; then
    echo "No complete previous deployment is available for rollback"
    exit "$failure_code"
  fi

  BACKEND_IMAGE="$PREVIOUS_BACKEND_IMAGE"
  FRONTEND_IMAGE="$PREVIOUS_FRONTEND_IMAGE"

  # 서버에 이전 이미지가 없으면 GHCR에서 다시 받는다.
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    if ! sudo docker image inspect "$image" >/dev/null 2>&1; then
      if ! sudo docker pull "$image"; then
        echo "Rollback failed: previous image is unavailable"
        exit "$failure_code"
      fi
    fi
  done

  # 이전 프론트·백엔드 이미지를 복구한다.
  if ! compose up -d --no-build --no-deps backend frontend; then
    echo "Rollback failed while restoring containers"
    exit "$failure_code"
  fi

  for attempt in $(seq 1 24); do
    if python3 "$RELEASE_DIR/check-deployment.py"; then
      rollback_ready=true
      break
    fi

    sleep 5
  done

  if [ "$rollback_ready" = true ]; then
    echo "Previous deployment restored successfully"
  else
    echo "Rollback containers started, but verification failed"
    compose ps
  fi

  # 복구됐더라도 이번 배포는 실패로 반환한다.
  # 마지막 성공 SHA와 이미지 기록은 갱신하지 않는다.
  exit "$failure_code"
}

if [ "${#services[@]}" -gt 0 ]; then
  : "${BACKEND_IMAGE:?Backend image is missing; run a full deployment}"
  : "${FRONTEND_IMAGE:?Frontend image is missing; run a full deployment}"

  # 변경되지 않은 서비스도 중지·삭제 상태면 함께 복구한다.
  for service in backend frontend; do
    running=$(sudo docker ps -q \
      --filter "label=com.docker.compose.project=unnie-app" \
      --filter "label=com.docker.compose.service=$service")

    if [ -z "$running" ] \
      && [[ " ${services[*]} " != *" $service "* ]]; then
      services+=("$service")
    fi
  done

  # 설정 검사와 이미지 다운로드는 기존 서비스 교체 전에 수행한다.
  compose config --quiet
  compose pull "${services[@]}"

  # 실제 컨테이너 교체부터 오류 발생 시 롤백한다.
  trap 'handle_deploy_failure "$?"' ERR

  compose up -d --no-build --no-deps "${services[@]}"

  ready=false

  for attempt in $(seq 1 60); do
    if python3 "$RELEASE_DIR/check-deployment.py"; then
      ready=true
      break
    fi

    sleep 5
  done

  if [ "$ready" != true ]; then
    handle_deploy_failure 1
  fi

  trap - ERR
fi

# 이미지 기록을 먼저 저장한다.
printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' \
  "$BACKEND_IMAGE" "$FRONTEND_IMAGE" \
  > "$STATE_DIR/images-$SHA.env.tmp"

mv "$STATE_DIR/images-$SHA.env.tmp" \
  "$STATE_DIR/images-$SHA.env"

# 모든 검증이 성공한 경우에만 성공 SHA를 갱신한다.
printf '%s\n' "$SHA" \
  > "$STATE_DIR/last-successful-sha.tmp"

mv "$STATE_DIR/last-successful-sha.tmp" \
  "$STATE_DIR/last-successful-sha"

echo "Deployment succeeded: $SHA"