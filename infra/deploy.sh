#!/usr/bin/env bash
set -euo pipefail

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
}

BACKEND_IMAGE=$(current_image backend)
FRONTEND_IMAGE=$(current_image frontend)

services=()

if [ "$BUILD_BACKEND" = true ]; then
  BACKEND_IMAGE="$IMAGE_BASE-backend:$SHA"
  services+=(backend)
fi

if [ "$BUILD_FRONTEND" = true ]; then
  FRONTEND_IMAGE="$IMAGE_BASE-frontend:$SHA"
  services+=(frontend)
fi

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

  compose() {
    sudo env \
      BACKEND_IMAGE="$BACKEND_IMAGE" \
      FRONTEND_IMAGE="$FRONTEND_IMAGE" \
      docker compose \
      -p unnie-app \
      --env-file "$ENV_FILE" \
      -f "$RELEASE_DIR/compose.app.yml" "$@"
  }

  compose config --quiet
  compose pull "${services[@]}"
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
    echo "Deployment verification failed; success marker is unchanged"
    compose ps
    compose logs --tail=80 backend frontend
    exit 1
  fi
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