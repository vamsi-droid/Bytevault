#!/usr/bin/env bash
# Thorough end-to-end check of the running system (start it with
# scripts/start-all.sh first): JWT edge cases, direct-to-service calls,
# validation, ownership rules, admin CRUD, load balancing, failover and
# health. Creates its own users/products and deletes them afterwards.
# Note: section G stops and restarts the second product-service (:8092).
cd "$(dirname "$0")/.."
GW=http://localhost:8080; H='Content-Type: application/json'
PASS=0; FAIL=0
check() { # desc expected actual
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); printf "  \033[32mPASS\033[0m %-62s %s\n" "$1" "$3"
  else FAIL=$((FAIL+1)); printf "  \033[31mFAIL\033[0m %-62s expected %s got %s\n" "$1" "$2" "$3"; fi; }
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
tok() { python3 -c "import sys,json; print(json.load(sys.stdin)['$1'])"; }

S=$(date +%s); A="chkA_$S"; B="chkB_$S"
AT=$(curl -s -X POST $GW/api/users/login -H "$H" -d '{"username":"admin","password":"admin123"}' | tok token)
TA=$(curl -s -X POST $GW/api/users/register -H "$H" -d "{\"username\":\"$A\",\"password\":\"secret123\",\"email\":\"$A@x.com\"}" | tok token)
TB=$(curl -s -X POST $GW/api/users/register -H "$H" -d "{\"username\":\"$B\",\"password\":\"secret123\",\"email\":\"$B@x.com\"}" | tok token)
P1=$(curl -s -X POST $GW/api/products -H "$H" -H "Authorization: Bearer $AT" -d '{"name":"Check P1","price":10,"fileType":"PDF"}' | tok id)
P2=$(curl -s -X POST $GW/api/products -H "$H" -H "Authorization: Bearer $AT" -d '{"name":"Check P2","price":20,"fileType":"ZIP"}' | tok id)

echo "== A. Each service enforces JWT itself (bypassing the gateway)"
check "user-service :8081 /api/users/me, no token"          401 $(code localhost:8081/api/users/me)
check "product-service :8082 POST product, no token"        401 $(code -X POST localhost:8082/api/products -H "$H" -d '{}')
check "product-service :8092 POST product, no token"        401 $(code -X POST localhost:8092/api/products -H "$H" -d '{}')
check "order-service :8083 /api/orders, no token"           401 $(code localhost:8083/api/orders)
BODY=$(curl -s localhost:8083/api/orders)
check "direct 401 has JSON error message"                  yes "$(echo "$BODY" | grep -q 'Missing or malformed' && echo yes || echo "no: $BODY")"
check "direct call with forged token -> 401"               401 $(code localhost:8082/api/products -X POST -H "$H" -H "Authorization: Bearer abc.def.ghi" -d '{}')
check "product-service :8082 GET catalog is public"         200 $(code localhost:8082/api/products)
check "user-service :8081 /api/users/me with valid token"   200 $(code localhost:8081/api/users/me -H "Authorization: Bearer $TA")

echo "== B. JWT edge cases"
EXP=$(python3 - <<'PY'
import hmac,hashlib,base64,json,time
b=lambda d: base64.urlsafe_b64encode(d).rstrip(b"=").decode()
k=b"bytevault-marketplace-super-secret-jwt-signing-key-2024-college-project-demo-only"
h=b(json.dumps({"alg":"HS512"}).encode()); p=b(json.dumps({"sub":"admin","role":"ADMIN","iat":int(time.time())-7200,"exp":int(time.time())-3600}).encode())
print(h+"."+p+"."+b(hmac.new(k,(h+"."+p).encode(),hashlib.sha512).digest()))
PY
)
check "expired (but correctly signed) token rejected"      401 $(code $GW/api/users/me -H "Authorization: Bearer $EXP")
FORGED=$(python3 -c "
import base64,json,sys
h,p,s='$TA'.split('.'); d=json.loads(base64.urlsafe_b64decode(p+'=='))
d['role']='ADMIN'; print(h+'.'+base64.urlsafe_b64encode(json.dumps(d).encode()).rstrip(b'=').decode()+'.'+s)")
check "USER token edited to role=ADMIN rejected"           401 $(code $GW/api/users -H "Authorization: Bearer $FORGED")
check "Authorization header without 'Bearer '"             401 $(code $GW/api/orders -H "Authorization: $TA")
check "wrong password"                                     401 $(code -X POST $GW/api/users/login -H "$H" -d "{\"username\":\"$A\",\"password\":\"nope123\"}")
check "unknown user login"                                 401 $(code -X POST $GW/api/users/login -H "$H" -d '{"username":"nobody_xyz","password":"nope123"}')

echo "== C. Validation and error handling"
check "duplicate username -> 409"                          409 $(code -X POST $GW/api/users/register -H "$H" -d "{\"username\":\"$A\",\"password\":\"secret123\",\"email\":\"a@x.com\"}")
check "register with bad email -> 400"                     400 $(code -X POST $GW/api/users/register -H "$H" -d '{"username":"zzz_bad","password":"secret123","email":"not-an-email"}')
check "register with short password -> 400"                400 $(code -X POST $GW/api/users/register -H "$H" -d '{"username":"zzz_bad","password":"123","email":"z@x.com"}')
check "product with negative price -> 400"                 400 $(code -X POST $GW/api/products -H "$H" -H "Authorization: Bearer $AT" -d '{"name":"X","price":-5,"fileType":"PDF"}')
check "GET missing product -> 404"                         404 $(code $GW/api/products/999999)
check "UPDATE missing product -> 404"                      404 $(code -X PUT $GW/api/products/999999 -H "$H" -H "Authorization: Bearer $AT" -d '{"name":"X","price":5,"fileType":"PDF"}')
check "order a missing product -> 404"                     404 $(code -X POST $GW/api/orders -H "$H" -H "Authorization: Bearer $TA" -d '{"productId":999999}')
check "admin GET missing user -> 404"                      404 $(code $GW/api/users/999999 -H "Authorization: Bearer $AT")
check "invalid role value -> 400"                          400 $(code -X PUT $GW/api/users/1/role -H "$H" -H "Authorization: Bearer $AT" -d '{"role":"SUPERUSER"}')

echo "== D. Authorization / ownership"
O1=$(curl -s -X POST $GW/api/orders -H "$H" -H "Authorization: Bearer $TA" -d "{\"productId\":$P1}" | tok orderId)
LINK=$(curl -s $GW/api/orders/$O1 -H "Authorization: Bearer $TA" | tok downloadLink)
DT=${LINK#*token=}
check "user B cannot read user A's order"                  404 $(code $GW/api/orders/$O1 -H "Authorization: Bearer $TB")
check "user B cannot cancel user A's order"                404 $(code -X PUT $GW/api/orders/$O1/cancel -H "Authorization: Bearer $TB")
check "user B cannot delete user A's order"                404 $(code -X DELETE $GW/api/orders/$O1 -H "Authorization: Bearer $TB")
check "USER cannot list all orders"                        403 $(code $GW/api/orders/all -H "Authorization: Bearer $TA")
check "USER cannot delete a product"                       403 $(code -X DELETE $GW/api/products/$P1 -H "Authorization: Bearer $TA")
check "USER cannot change roles"                           403 $(code -X PUT $GW/api/users/1/role -H "$H" -H "Authorization: Bearer $TA" -d '{"role":"ADMIN"}')
check "admin cannot delete own account via /{id}"          403 $(code -X DELETE $GW/api/users/2 -H "Authorization: Bearer $AT")
check "download token works for its product"               200 $(code "$GW/api/products/$P1/download?token=$DT")
check "download token rejected for a different product"   401 $(code "$GW/api/products/$P2/download?token=$DT")
check "download token can't be used as login token"        401 $(code $GW/api/orders -H "Authorization: Bearer $DT")
check "login token can't be used as download token"        401 $(code "$GW/api/products/$P1/download?token=$TA")
check "cancel PAID order -> 200"                           200 $(code -X PUT $GW/api/orders/$O1/cancel -H "Authorization: Bearer $TA")
check "cancel it again -> 409"                             409 $(code -X PUT $GW/api/orders/$O1/cancel -H "Authorization: Bearer $TA")
STATUS=$(curl -s $GW/api/orders/$O1 -H "Authorization: Bearer $TA" | tok status)
check "cancelled order persisted as CANCELLED"             CANCELLED $STATUS

echo "== E. Admin user CRUD"
BID=$(curl -s $GW/api/users/me -H "Authorization: Bearer $TB" | tok id)
check "admin promotes B to ADMIN"                          200 $(code -X PUT $GW/api/users/$BID/role -H "$H" -H "Authorization: Bearer $AT" -d '{"role":"ADMIN"}')
TB2=$(curl -s -X POST $GW/api/users/login -H "$H" -d "{\"username\":\"$B\",\"password\":\"secret123\"}" | tok token)
check "B's new token now has ADMIN rights"                 200 $(code $GW/api/orders/all -H "Authorization: Bearer $TB2")
check "admin updates B's email"                            200 $(code -X PUT $GW/api/users/$BID -H "$H" -H "Authorization: Bearer $AT" -d '{"email":"b-new@x.com"}')
check "admin deletes B"                                    204 $(code -X DELETE $GW/api/users/$BID -H "Authorization: Bearer $AT")
check "B can no longer log in"                             401 $(code -X POST $GW/api/users/login -H "$H" -d "{\"username\":\"$B\",\"password\":\"secret123\"}")
check "A changes own password"                             200 $(code -X PUT $GW/api/users/me -H "$H" -H "Authorization: Bearer $TA" -d '{"password":"newpass456"}')
check "A logs in with the new password"                    200 $(code -X POST $GW/api/users/login -H "$H" -d "{\"username\":\"$A\",\"password\":\"newpass456\"}")
check "order history survives product deletion"           204 $(code -X DELETE $GW/api/products/$P1 -H "Authorization: Bearer $AT")
check "  ...A's order list still loads"                    200 $(code $GW/api/orders -H "Authorization: Bearer $TA")

echo "== F. Load balancing"
for i in $(seq 1 10); do curl -s $GW/api/products/instance | grep -o 'product-service:[0-9]*'; done | sort | uniq -c > logs/.lb-check.txt
check "gateway: 10 requests split 5/5 across instances"    "5 5" "$(awk '{print $1}' logs/.lb-check.txt | xargs)"
# Each purchase makes 2 Feign calls (get product + mint download token); count both on each instance
feign() { grep -cE "(GET /api/products/[0-9]+|download-token) served by" "$1"; }
before82=$(feign logs/product-service.log); before92=$(feign logs/product-service-2.log)
for i in 1 2 3 4 5; do curl -s -o /dev/null -X POST $GW/api/orders -H "$H" -H "Authorization: Bearer $TA" -d "{\"productId\":$P2}"; done
d82=$(( $(feign logs/product-service.log) - before82 )); d92=$(( $(feign logs/product-service-2.log) - before92 ))
check "Feign (order->product): 10 calls split across instances" "5/5" "$d82/$d92"
H82=$(curl -s -D - -o /dev/null $GW/api/products | grep -i x-served-by | tr -d '\r' | awk '{print $2}')
check "X-Served-By header present through gateway"         yes "$([ -n "$H82" ] && echo yes || echo no)"

echo "== G. Failover and recovery"
kill $(cat logs/product-service-2.pid); rm -f logs/product-service-2.pid
errs=0; for i in $(seq 1 15); do c=$(code $GW/api/products); [ "$c" = 200 ] || errs=$((errs+1)); sleep 0.5; done
check "15 catalog requests while :8092 shuts down, errors"  0 $errs
PORT=8092 nohup java -jar product-service/target/product-service-1.0.0.jar > logs/product-service-2.log 2>&1 & echo $! > logs/product-service-2.pid
back=no; for i in $(seq 1 60); do curl -s $GW/api/products/instance | grep -q ':8092' && { back=yes; break; }; sleep 1; done
check "restarted :8092 rejoins rotation (within 60s)"      yes $back

echo "== H. Pages and health"
check "dashboard http://localhost:8080/"                   200 $(code $GW/)
check "Eureka dashboard http://localhost:8761/"            200 $(code http://localhost:8761/)
for p in 8080 8081 8082 8083 8092; do check "actuator health :$p" 200 $(code localhost:$p/actuator/health); done
for s in user product order; do check "gateway status route /status/$s-service/info" 200 $(code $GW/status/$s-service/info); done

# cleanup
curl -s -o /dev/null -X DELETE $GW/api/products/$P2 -H "Authorization: Bearer $AT"
for o in $(curl -s $GW/api/orders -H "Authorization: Bearer $TA" | python3 -c "import sys,json;[print(o['orderId']) for o in json.load(sys.stdin)]"); do curl -s -o /dev/null -X DELETE $GW/api/orders/$o -H "Authorization: Bearer $TA"; done
curl -s -o /dev/null -X DELETE $GW/api/users/me -H "Authorization: Bearer $TA"

rm -f logs/.lb-check.txt
echo; echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
