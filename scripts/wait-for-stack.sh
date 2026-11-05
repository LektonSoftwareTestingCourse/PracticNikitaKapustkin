#!/usr/bin/env bash
# Ждёт, пока стек начнёт отвечать. docker compose up --wait здесь не подходит: на холодном старте
# JVM поднимаются дольше start_period, контейнер на время становится unhealthy, и --wait сразу падает.
set -u

TIMEOUT_SECONDS="${1:-300}"
URLS=(
  "http://localhost:8080/health"
  "http://localhost:8084/health"
  "http://localhost:8085/health"
  "http://localhost:8096/actuator/health"
  "http://localhost:8097/actuator/health"
)

deadline=$((SECONDS + TIMEOUT_SECONDS))
for url in "${URLS[@]}"; do
  until curl -sf -m 3 -o /dev/null "$url"; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Stack is not ready after ${TIMEOUT_SECONDS}s: $url" >&2
      docker compose ps >&2
      exit 1
    fi
    sleep 3
  done
done
echo "Stack is ready after ${SECONDS}s"
