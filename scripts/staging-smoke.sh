#!/bin/sh
set -eu

base_url="${1:?Usage: staging-smoke.sh https://staging.example.com}"
base_url="${base_url%/}"
cors_origin="${SMOKE_CORS_ORIGIN:-$base_url}"
attempts="${SMOKE_ATTEMPTS:-30}"
delay="${SMOKE_DELAY_SECONDS:-10}"
curl_extra=""
if [ -n "${SMOKE_RESOLVE:-}" ]; then
  curl_extra="--resolve ${SMOKE_RESOLVE}"
fi

wait_for() {
  name="$1"
  url="$2"
  attempt=1
  while [ "$attempt" -le "$attempts" ]; do
    # shellcheck disable=SC2086
    if curl $curl_extra --fail --silent --show-error --max-time 10 "$url" >/dev/null; then
      printf '%s: ok\n' "$name"
      return 0
    fi
    printf '%s: attempt %s/%s failed\n' "$name" "$attempt" "$attempts" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
  done
  printf '%s did not become healthy: %s\n' "$name" "$url" >&2
  return 1
}

has_header() {
  header_name="$1"
  header_value="$2"
  header_file="$3"
  awk -v name="$header_name" -v value="$header_value" '
    BEGIN { name = tolower(name) }
    {
      colon = index($0, ":")
      if (colon == 0) {
        next
      }
      field = tolower(substr($0, 1, colon - 1))
      actual = substr($0, colon + 1)
      sub(/^[ \t]+/, "", actual)
      if (field == name && actual == value) {
        found = 1
      }
    }
    END { exit found ? 0 : 1 }
  ' "$header_file"
}

wait_for "frontend" "$base_url/healthz"
wait_for "liveness" "$base_url/health/live"
wait_for "readiness" "$base_url/health/ready"

headers="$(mktemp)"
body="$(mktemp)"
cors_headers="$(mktemp)"
normalized_headers="$(mktemp)"
normalized_cors_headers="$(mktemp)"
trap 'rm -f "$headers" "$body" "$cors_headers" "$normalized_headers" "$normalized_cors_headers"' EXIT

status="$(
  curl $curl_extra --silent --show-error \
    --dump-header "$headers" \
    --output "$body" \
    --write-out '%{http_code}' \
    -H 'X-Request-ID: staging-smoke-test' \
    "$base_url/api/auth/me"
)"

if [ "$status" != "401" ]; then
  printf 'Expected unauthenticated /api/auth/me to return 401, got %s\n' "$status" >&2
  exit 1
fi

tr -d '\r' < "$headers" > "$normalized_headers"
if ! has_header 'X-Request-ID' 'staging-smoke-test' "$normalized_headers"; then
  printf 'API response did not preserve the supplied X-Request-ID\n' >&2
  sed -n '1,40p' "$normalized_headers" >&2
  exit 1
fi

if ! grep -q '"requestId":"staging-smoke-test"' "$body"; then
  printf 'API error body did not include the request ID\n' >&2
  exit 1
fi

curl $curl_extra --silent --show-error --output /dev/null --dump-header "$cors_headers" \
  -X OPTIONS \
  -H "Origin: $cors_origin" \
  -H 'Access-Control-Request-Method: POST' \
  "$base_url/api/auth/login"

tr -d '\r' < "$cors_headers" > "$normalized_cors_headers"
if ! has_header 'Access-Control-Allow-Origin' "$cors_origin" "$normalized_cors_headers"; then
  printf 'Expected CORS origin was not returned\n' >&2
  sed -n '1,40p' "$normalized_cors_headers" >&2
  exit 1
fi

printf 'staging smoke tests: passed\n'
