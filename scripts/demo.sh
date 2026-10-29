#!/usr/bin/env bash
# Walks through every review rubric against the running system (start it with
# scripts/start-all.sh first). Every call goes through the API gateway (:8080).
set -uo pipefail
GW="${GW:-http://localhost:8080}"
U="demo_$(date +%s)"

section() { printf "\n\033[1;34m=== %s ===\033[0m\n" "$1"; }
call() { # METHOD PATH [TOKEN] [JSON]
  local args=(-s -X "$1" "$GW$2" -w "\n  -> HTTP %{http_code}\n")
  [ -n "${3:-}" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H "Content-Type: application/json" -d "$4")
  printf "\033[33m%s %s\033[0m\n" "$1" "$2"
  curl "${args[@]}"
}
json() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

if ! curl -sf "$GW/api/users/instance" >/dev/null || ! curl -sf "$GW/api/products/instance" >/dev/null \
   || ! curl -sf "$GW/api/orders/instance" >/dev/null; then
  echo "The gateway can't reach every service yet. Run scripts/start-all.sh (or wait a few seconds) and retry."
  exit 1
fi

section "1. Microservices registered in Eureka"
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | python3 -c '
import sys,json
for a in json.load(sys.stdin)["applications"]["application"]:
    print("  %-16s %s" % (a["name"], ", ".join(i["instanceId"] for i in a["instance"])))'

section "2. JWT authentication"
echo "-- protected endpoint without a token is rejected at the gateway"
call GET /api/orders
echo "-- forged token is rejected"
call GET /api/users/me "not.a.real.token"
echo "-- register a new USER -> returns a signed JWT"
USER_TOKEN=$(curl -s -X POST "$GW/api/users/register" -H "Content-Type: application/json" \
  -d "{\"username\":\"$U\",\"password\":\"secret123\",\"email\":\"$U@example.com\"}" | json '["token"]')
echo "  token: ${USER_TOKEN:0:40}..."
echo "-- login as the seeded ADMIN"
ADMIN_TOKEN=$(curl -s -X POST "$GW/api/users/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | json '["token"]')
echo "  token: ${ADMIN_TOKEN:0:40}..."
echo "-- token identifies the caller"
call GET /api/users/me "$USER_TOKEN"
echo "-- role check: a USER cannot create products"
call POST /api/products "$USER_TOKEN" '{"name":"Nope","price":1,"fileType":"pdf"}'

section "3. CRUD - products (ADMIN)"
PID=$(curl -s -X POST "$GW/api/products" -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Spring Boot eBook","price":19.99,"fileType":"pdf"}' | json '["id"]')
echo "  CREATE -> product id $PID"
call GET "/api/products/$PID"
call PUT "/api/products/$PID" "$ADMIN_TOKEN" '{"name":"Spring Boot eBook (2nd ed.)","price":24.99,"fileType":"pdf"}'
call GET /api/products

section "3. CRUD - users"
call PUT /api/users/me "$USER_TOKEN" "{\"email\":\"$U@bytevault.dev\"}"
call GET /api/users "$ADMIN_TOKEN"
echo "-- a USER cannot list every account"
call GET /api/users "$USER_TOKEN"

section "3. CRUD - orders (via Feign -> product-service)"
OID=$(curl -s -X POST "$GW/api/orders" -H "Authorization: Bearer $USER_TOKEN" -H "Content-Type: application/json" \
  -d "{\"productId\":$PID}" | json '["orderId"]')
echo "  CREATE -> order id $OID"
LINK=$(curl -s "$GW/api/orders/$OID" -H "Authorization: Bearer $USER_TOKEN" | json '["downloadLink"]')
call GET "/api/orders/$OID" "$USER_TOKEN"
echo "-- the download token from the purchase works"
call GET "$LINK"
echo "-- a tampered download token does not"
call GET "/api/products/$PID/download?token=tampered"
call PUT "/api/orders/$OID/cancel" "$USER_TOKEN"
call DELETE "/api/orders/$OID" "$USER_TOKEN"
call GET /api/orders "$USER_TOKEN"

section "4. Load balancing (gateway -> lb://product-service)"
for i in 1 2 3 4 5 6; do
  printf "  request %d -> " "$i"
  curl -s "$GW/api/products/instance" | json '["instance"]'
done
echo "  (with two product-service instances running, the instance alternates)"

section "Cleanup"
call DELETE "/api/products/$PID" "$ADMIN_TOKEN"
call DELETE /api/users/me "$USER_TOKEN"
