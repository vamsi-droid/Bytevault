# Review Notes

## Before the review

1. Postgres must be running on `localhost:5434` (it starts automatically at
   login as a Homebrew service; `brew services start postgresql@15` if not).
2. `scripts/start-all.sh` — builds and starts everything, including a second
   `product-service` instance on 8092. Wait ~10s after it finishes.
3. Open http://localhost:8761 (Eureka) and http://localhost:8080 (dashboard).
4. `scripts/demo.sh` — walks through every rubric with labelled output.
5. Afterwards: `scripts/stop-all.sh`.

## Rubric → what to show

| Rubric | Show | Code |
|---|---|---|
| Microservice implementation | Eureka page: 5 registered apps, `PRODUCT-SERVICE` with 2 instances | Each module's `*Application.java`, `order-service/.../client/ProductClient.java` (Feign) |
| JWT authentication | `demo.sh` section 2: 401 without/forged token, login returns token, `/me`, 403 for USER doing ADMIN work | `user-service/.../security/JwtUtil.java`, `api-gateway/.../filter/JwtAuthGlobalFilter.java`, `JwtAuthFilter` in each service |
| CRUD | `demo.sh` section 3: users, products, orders | `UserController`, `ProductController`, `OrderController` |
| Load balancing | `demo.sh` section 4: alternating `product-service:8082` / `:8092`; `X-Served-By` header | `lb://` routes in `api-gateway/src/main/resources/application.yml`, `@FeignClient(name = "product-service")` |

## Demo accounts and data

| Role | Username | Password | Notes |
|---|---|---|---|
| ADMIN | `admin` | `admin123` | Created automatically on startup (`AdminSeeder`) |
| USER | `buyer` | `buyer123` | Has one past order (Spring Boot Microservices Handbook) |

The catalog holds 10 sample products (ebooks, courses, a license key,
assets). Data lives in Postgres, so it survives restarts. Register more
users from the dashboard's Account tab.

The dashboard login has a captcha (checked in the browser only).

## Checking everything

`scripts/full-check.sh` runs 60 end-to-end checks against the running system
(JWT edge cases, direct-to-service calls, validation, ownership, admin CRUD,
load balancing, failover, health) and cleans up after itself.

## Likely questions

**Why verify the JWT in every service and not just the gateway?**
Defense in depth. If a request reaches a service directly (bypassing the
gateway on 8080), the service still rejects it. Every service shares the
same signing secret, so each can verify the token without calling
user-service.

**What is in the token?**
Subject = username, a `role` claim (USER/ADMIN), issued-at and a 1-hour
expiry, signed with HMAC-SHA512 (JJWT). It is stateless: nothing is stored
server-side.

**Where does load balancing happen?**
Client-side, in Spring Cloud LoadBalancer (round-robin). The gateway's
`lb://product-service` route and order-service's Feign client both ask Eureka
for the live instances of `product-service` and alternate between them. No
separate load-balancer server is needed.

**How does a second instance join?**
Start the same jar with another port (`PORT=8092`). It registers in Eureka
under the same service name with a unique instance id
(`product-service:8092`); within ~5s the gateway and Feign start sending it
traffic.

**Why is the download token separate from the login token?**
Different secret and a 5-minute expiry, scoped to one product with
`purpose=download`. A leaked download link can't be used to log in or to
download anything else.

**Why does each service have its own database?**
Each service owns its data (database-per-service). order-service never reads
the products table; it asks product-service over Feign.

**What happens if product-service is down?**
With two product-service instances, nothing breaks: the stopped instance
deregisters from Eureka, and the gateway's `Retry` filter re-sends any GET
that hit it to the other instance. If *every* instance is down, catalog and
purchase calls fail, but login still works (user-service is independent).

## Optional live failover demo

```
for i in $(seq 1 20); do curl -s localhost:8080/api/products/instance; echo; sleep 1; done
# in a second terminal, while that loop runs:
kill $(cat logs/product-service-2.pid)
```
The loop switches from alternating 8082/8092 to only 8082, with no errors.
Bring it back with `PORT=8092 nohup java -jar product-service/target/product-service-1.0.0.jar > logs/product-service-2.log 2>&1 & echo $! > logs/product-service-2.pid`
and it rejoins the rotation within ~10s.
