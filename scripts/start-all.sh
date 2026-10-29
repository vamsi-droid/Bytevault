#!/usr/bin/env bash
# Builds every module and starts the whole system in the background, including
# a SECOND product-service instance (port 8092) so load balancing is visible.
# Logs go to logs/<name>.log. Stop everything with scripts/stop-all.sh.
#
#   scripts/start-all.sh            # 1 instance of each + 2nd product-service
#   EXTRA_INSTANCES=0 scripts/start-all.sh   # single instance of everything
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p logs

EXTRA_INSTANCES="${EXTRA_INSTANCES:-1}"

if (exec 3<>/dev/tcp/localhost/8761) 2>/dev/null; then
  echo "Services already seem to be running (port 8761 is in use). Run scripts/stop-all.sh first."
  exit 1
fi

echo "==> Checking Postgres on :5434"
if ! (exec 3<>/dev/tcp/localhost/5434) 2>/dev/null; then
  echo "    Postgres is not running on localhost:5434. Start it first, e.g.:"
  echo "      brew services start postgresql@15"
  exit 1
fi
for db in bytevault_users bytevault_products bytevault_orders; do
  if ! PGPASSWORD=postgres psql -h localhost -p 5434 -U postgres -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw "$db"; then
    echo "    creating database $db"
    PGPASSWORD=postgres createdb -h localhost -p 5434 -U postgres "$db"
  fi
done

echo "==> Building"
mvn -q -DskipTests clean package

wait_for() { # name url
  printf "    waiting for %s " "$1"
  for _ in $(seq 1 90); do
    if curl -sf "$2" >/dev/null 2>&1; then echo "up"; return 0; fi
    printf "."; sleep 1
  done
  echo " TIMED OUT (see logs/$1.log)"; return 1
}

start() { # name jar port
  echo "==> Starting $1 on :$3"
  PORT="$3" nohup java -jar "$2" >"logs/$1.log" 2>&1 &
  echo $! >"logs/$1.pid"
}

start eureka-server   eureka-server/target/eureka-server-1.0.0.jar     8761
wait_for eureka-server http://localhost:8761/

start user-service      user-service/target/user-service-1.0.0.jar       8081
start product-service   product-service/target/product-service-1.0.0.jar 8082
start order-service     order-service/target/order-service-1.0.0.jar     8083
if [ "$EXTRA_INSTANCES" = "1" ]; then
  start product-service-2 product-service/target/product-service-1.0.0.jar 8092
fi

wait_for user-service    http://localhost:8081/actuator/health
wait_for product-service http://localhost:8082/actuator/health
wait_for order-service   http://localhost:8083/actuator/health
if [ "$EXTRA_INSTANCES" = "1" ]; then
  wait_for product-service-2 http://localhost:8092/actuator/health
fi

start api-gateway api-gateway/target/api-gateway-1.0.0.jar 8080
wait_for api-gateway http://localhost:8080/actuator/health

# "Up" isn't enough: the gateway also has to have discovered every service
# through Eureka before requests route. Wait until each one answers through it.
for svc in users products orders; do
  wait_for "gateway -> $svc" "http://localhost:8080/api/$svc/instance"
done
if [ "$EXTRA_INSTANCES" = "1" ]; then
  printf "    waiting for gateway to balance across both product-service instances "
  seen=""
  for _ in $(seq 1 60); do
    seen="$seen $(curl -s http://localhost:8080/api/products/instance | grep -o 'product-service:[0-9]*')"
    if echo "$seen" | grep -q ':8082' && echo "$seen" | grep -q ':8092'; then echo "ok"; break; fi
    printf "."; sleep 1
  done
fi

echo
echo "All services up."
echo "  Gateway / dashboard : http://localhost:8080/"
echo "  Eureka registry     : http://localhost:8761/"
echo "  Run the demo        : scripts/demo.sh"
