#!/usr/bin/env bash

set -euo pipefail

mode="dry-run"
if [[ "${1:-}" == "--apply" ]]; then
  mode="apply"
elif [[ -n "${1:-}" && "${1:-}" != "--dry-run" ]]; then
  echo "Usage: $0 [--dry-run|--apply]" >&2
  exit 2
fi

redis_host="${REDIS_HOST:-127.0.0.1}"
redis_port="${REDIS_PORT:-6379}"
redis_db="${REDIS_DB:-0}"
redis_user="${REDIS_USER:-}"

redis_args=(-h "$redis_host" -p "$redis_port" -n "$redis_db" --raw)
if [[ -n "$redis_user" ]]; then
  redis_args+=(--user "$redis_user")
fi

patterns=(
  'llm:u_cfg:*'
  'llm:u_def:*'
  'llm:cfg:*'
  'llm:pvd:*'
  'llm:user:*'
  'llm:system:*'
  'user:info:*'
  'user:role:*'
  'document:file-upload:config'
  'knowledge:file-upload:config'
)

matched=0
deleted=0

for pattern in "${patterns[@]}"; do
  while IFS= read -r key; do
    [[ -z "$key" ]] && continue
    matched=$((matched + 1))
    if [[ "$mode" == "apply" ]]; then
      redis-cli "${redis_args[@]}" UNLINK "$key" >/dev/null
      deleted=$((deleted + 1))
      echo "deleted $key"
    else
      echo "would-delete $key"
    fi
  done < <(redis-cli "${redis_args[@]}" --scan --pattern "$pattern")
done

if [[ "$mode" == "apply" ]]; then
  echo "Cleanup complete: matched=$matched deleted=$deleted"
else
  echo "Dry run complete: matched=$matched. Re-run with --apply to delete."
fi
