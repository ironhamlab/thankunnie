#!/usr/bin/env bash
set -euo pipefail

SHA="${1:?Commit SHA is required}"
IMAGE_BASE="${2:?Image base is required}"
BUILD_BACKEND="${3:?Backend flag is required}"
BUILD_FRONTEND="${4:?Frontend flag is required}"

[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || {
  echo "Invalid commit SHA"
  exit 1
}

RELEASE_DIR="/home/ubuntu/unnie/releases/$SHA"
STATE_DIR="/home/ubuntu/unnie/deploy-state"
ENV_FILE="/home/ubuntu/unnie/infra/.env"

mkdir -p "$STATE_DIR"
test -f "$ENV_FILE"

# 수동 실행과의 배포 충돌도 방지한다.
exec 9>"$STATE_DIR/deploy.lock"
flock -n 9 || {
  echo "Another deployment is running"
  exit 1
}

# 현재 실행 중인 이미지로부터 변경되지 않은 서비스 버전을 유지한다.
current_image() {
  local service="$1"
  local container

  container=$(sudo docker ps -q \
    --filter "label=com.docker.compose.project=unnie-app" \
    --filter "label=com.docker.compose.service=$service")

  if [ -n "$container" ]; then
    sudo docker inspect --format '{{.Config.Image}}' "$container"
  fi
}

BACKEND_IMAGE=$(current_image backend)
FRONTEND_IMAGE=$(current_image frontend)

services=()

if [ "$BUILD_BACKEND" = "true" ]; then
  BACKEND_IMAGE="$IMAGE_BASE-backend:$SHA"
  services+=(backend)
fi

if [ "$BUILD_FRONTEND" = "true" ]; then
  FRONTEND_IMAGE="$IMAGE_BASE-frontend:$SHA"
  services+=(frontend)
fi

if [ "${#services[@]}" -gt 0 ]; then
  : "${BACKEND_IMAGE:?Backend image is missing}"
  : "${FRONTEND_IMAGE:?Frontend image is missing}"

  export BACKEND_IMAGE FRONTEND_IMAGE

  compose() {
    sudo env \
      BACKEND_IMAGE="$BACKEND_IMAGE" \
      FRONTEND_IMAGE="$FRONTEND_IMAGE" \
      docker compose \
      --env-file "$ENV_FILE" \
      -f "$RELEASE_DIR/compose.app.yml" "$@"
  }

  compose config --quiet
  compose pull "${services[@]}"
  compose up -d --no-build --no-deps "${services[@]}"

  # Nginx를 거쳐 백엔드의 공통 404 응답이 오는지 확인한다.
  ready=false

  for attempt in $(seq 1 60); do
    status=$(curl -s --max-time 5 \
      -o "$STATE_DIR/check-response.json" \
      -w '%{http_code}' \
      http://127.0.0.1/api/infra-check) || status=000

    if [ "$status" = "404" ] \
      && grep -q '"COMMON_404"' "$STATE_DIR/check-response.json"; then
      ready=true
      break
    fi

    sleep 5
  done

  if [ "$ready" != "true" ]; then
    echo "Deployment verification failed"
    compose ps
    compose logs --tail=80 backend frontend
    exit 1
  fi

  # 문제 발생 시 참고할 배포 이미지 기록
  printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' \
    "$BACKEND_IMAGE" "$FRONTEND_IMAGE" \
    > "$STATE_DIR/images-$SHA.env"
fi

# 배포가 성공한 경우에만 비교 기준을 갱신한다.
printf '%s\n' "$SHA" > "$STATE_DIR/last-successful-sha.tmp"
mv "$STATE_DIR/last-successful-sha.tmp" \
  "$STATE_DIR/last-successful-sha"

echo "Deployment succeeded: $SHA"