#!/bin/sh
set -eu

# The app validates that a token's `iss` equals the configured issuer URI, so it must fetch
# discovery/JWKS from that same URL. That URL points at the reverse proxy (host.docker.internal
# -> host gateway -> published NGINX -> Keycloak), so wait for it to answer before booting Spring
# (whose OAuth2 client does discovery eagerly at startup).
WAIT_URL="${KEYCLOAK_WAIT_URL:-http://host.docker.internal:8080/realms/demo/.well-known/openid-configuration}"

echo "Waiting for Keycloak discovery at ${WAIT_URL} ..."
attempt=0
until curl -fsS "${WAIT_URL}" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "${attempt}" -ge 60 ]; then
    echo "Keycloak did not become ready in time" >&2
    exit 1
  fi
  sleep 3
done
echo "Keycloak is ready."

exec java ${JAVA_OPTS:-} -jar /app/app.jar
