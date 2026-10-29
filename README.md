# ByteVault Marketplace — Digital Asset Rights & Marketplace Delivery Engine

## 1. Problem Analysis

Physical-goods marketplaces solve delivery with shipping. A **digital**
marketplace (ebooks, software licenses, media files, design assets) has a
different problem: once a buyer has paid, the system must prove *who* is
allowed to fetch the file and make sure that right can't be shared, guessed,
or replayed by someone who never paid. Doing catalog browsing, purchasing,
and file access all as one monolith also means every part scales and fails
together, and a bug in checkout can take the whole product catalog down with
it.

**Problem statement:** design and build a system that separates identity,
catalog, and fulfillment concerns, so that:
- anyone can browse the catalog without an account,
- only an authenticated buyer can purchase a product,
- a purchase issues a *time-limited, single-purpose* proof of entitlement
  (not a permanent link), and
- each concern can be deployed, scaled, and reasoned about independently.

## 2. Requirement Specification

**Functional requirements**
- FR1 — A user can register and log in; the system issues a signed login
  token on success.
- FR2 — Anyone (authenticated or not) can browse the product catalog.
- FR3 — Only an ADMIN can create, update, or delete catalog products.
- FR4 — An authenticated user can purchase a product.
- FR5 — A purchase issues a short-lived, product-specific download token,
  separate from the login token.
- FR6 — A user can list their own past orders and re-derive the download
  link for any of them.
- FR7 — A single entry point routes every client request to the correct
  backend service without the client needing to know service locations.

**Non-functional requirements**
- NFR1 — Services discover each other at runtime (no hard-coded hosts/ports).
- NFR2 — Every protected endpoint independently rejects an invalid/expired
  token, even if a request somehow bypasses the gateway.
- NFR3 — The login-signing secret and the download-token-signing secret are
  cryptographically separate, so a leaked download link can never be used to
  forge a login session.
- NFR4 — Each service owns its own database/schema (no shared tables).

## 3. Microservice Identification

The problem was decomposed along data-ownership boundaries, not along
technical layers:

| Service | Owns | Why it's separate |
|---|---|---|
| `eureka-server` | Service registry | Infra concern, not domain logic |
| `api-gateway` | Routing + edge auth | Single client-facing entry point |
| `user-service` | Accounts, credentials, login JWTs | Identity is its own bounded context |
| `product-service` | Catalog, download-token issuance | Digital assets and their access rules |
| `order-service` | Purchases, order history | Purchasing is a distinct workflow that *reads* product data but doesn't own it |

`order-service` never touches the products table directly — it calls
`product-service` over Feign (via its Eureka-registered name), which is what
keeps ownership of "what is a valid product" in one place.

## 4. Service Discovery

`eureka-server` (port `8761`) is a standalone Netflix Eureka registry
(`register-with-eureka: false`, `fetch-registry: false` — it doesn't register
with itself). Every other service:
1. Registers itself under `spring.application.name` (`user-service`,
   `product-service`, `order-service`, `api-gateway`).
2. Points `eureka.client.service-url.defaultZone` at
   `http://localhost:8761/eureka/`.

`order-service` resolves `product-service` purely by name through a
`@FeignClient(name = "product-service")` interface — Spring Cloud
LoadBalancer resolves that name to a live instance via Eureka at call time,
so no service ever hard-codes another's host or port.

## 5. JWT Authentication

- `user-service` issues the **login JWT** on `/api/users/register` and
  `/api/users/login` (HMAC-SHA, subject = username, claim = role, 1h expiry).
- `user-service`, `product-service`, and `order-service` all share the same
  signing secret (see each `application.yml`), so **each service verifies
  the login JWT itself** — none of them trust the gateway blindly.
- `product-service` also issues a **second, independent JWT** — the download
  token — with its own secret and a 5-minute expiry, scoped to one product
  and one purpose (`claims.purpose == "download"`). This is what a buyer
  actually uses to hit `GET /api/products/{id}/download`; it carries no
  login authority.
- Role is enforced at the endpoint level: `product-service` requires
  `role == ADMIN` (via the `authRole` request attribute set by its JWT
  filter) to create/update/delete products; anyone authenticated can
  purchase. `user-service` and `order-service` apply the same check to their
  admin-only endpoints.
- `user-service` verifies the token too (its own `JwtAuthFilter`): only
  register, login and `/api/users/instance` are public.
- Registration always creates a `USER`. A default `ADMIN` account
  (`admin` / `admin123`, override with `ADMIN_USERNAME` / `ADMIN_PASSWORD` /
  `ADMIN_EMAIL`) is created on first startup by `AdminSeeder`.

## 6. CRUD Operations

All calls go through the gateway (`http://localhost:8080`).

| Resource | Create | Read | Update | Delete |
|---|---|---|---|---|
| Users | `POST /api/users/register` | `GET /api/users/me`; `GET /api/users`, `GET /api/users/{id}` (ADMIN) | `PUT /api/users/me`; `PUT /api/users/{id}`, `PUT /api/users/{id}/role` (ADMIN) | `DELETE /api/users/me`; `DELETE /api/users/{id}` (ADMIN) |
| Products | `POST /api/products` (ADMIN) | `GET /api/products`, `GET /api/products/{id}` (public) | `PUT /api/products/{id}` (ADMIN) | `DELETE /api/products/{id}` (ADMIN) |
| Orders | `POST /api/orders` | `GET /api/orders`, `GET /api/orders/{id}`; `GET /api/orders/all` (ADMIN) | `PUT /api/orders/{id}/cancel` | `DELETE /api/orders/{id}` |

Users only ever see and change their own orders (`findByIdAndUsername`).
Cancelling an order sets it to `CANCELLED` and revokes its download link.

## 7. Load Balancing

Load balancing is client-side, through Spring Cloud LoadBalancer
(round-robin) over the instances Eureka reports:

- **Gateway → services**: every route uses `lb://<service-name>`.
- **Service → service**: `order-service`'s `@FeignClient(name =
  "product-service")` balances its calls the same way.

To run more than one instance of a service, start it again with a different
`PORT` (each service's `server.port` is `${PORT:<default>}`), e.g.
`PORT=8092 mvn -pl product-service spring-boot:run`. Each instance registers
under its own `instance-id` (`<name>:<port>`), and the Eureka/LoadBalancer
refresh intervals are shortened to 5s so a new instance gets traffic almost
immediately.

To see which instance answered:
- every response carries an `X-Served-By: <service>:<port>` header, and each
  instance logs the requests it handles;
- `GET /api/{users|products|orders}/instance` (public) returns the instance id.
  Calling `GET /api/products/instance` repeatedly with two product-service
  instances running alternates `product-service:8082` /
  `product-service:8092`.

## 8. API Gateway Configuration

`api-gateway` (port `8080`, Spring Cloud Gateway) is the single entry point:

- **Routing** — `/api/users/**`, `/api/products/**`, `/api/orders/**` are
  each routed to `lb://<service-name>`, load-balanced across whatever
  instances are currently registered in Eureka.
- **Edge authentication** — a `GlobalFilter`
  (`JwtAuthGlobalFilter`) runs before every proxied request. It allow-lists
  exactly the routes that must stay public (register, login, catalog
  browsing, the token-guarded download endpoint) and rejects everything else
  with `401` if the `Authorization` header is missing or the JWT doesn't
  verify — before the request ever reaches a backend service.
- **Retry on failure** — a default `Retry` filter re-sends a failed `GET`
  (5xx / connection refused) up to twice; the load balancer picks another
  instance, so losing one instance is invisible to clients. Non-GET requests
  are not retried, to avoid e.g. duplicate orders.
- This is defense in depth, not a replacement for per-service checks:
  `product-service` and `order-service` still verify the same token again,
  so calling a service directly (bypassing the gateway) is still safe.

## 9. Running locally

The quickest way, which also starts a second `product-service` instance on
`8092`:

```
scripts/start-all.sh    # build + start everything in the background (logs/)
scripts/demo.sh         # walks through every feature with curl, via the gateway
scripts/stop-all.sh
```

Or start each service by hand:

```
mvn -pl eureka-server spring-boot:run       # 8761
mvn -pl user-service spring-boot:run        # 8081
mvn -pl product-service spring-boot:run     # 8082
mvn -pl order-service spring-boot:run       # 8083
mvn -pl api-gateway spring-boot:run         # 8080, entry point
PORT=8092 mvn -pl product-service spring-boot:run   # optional 2nd instance
```

Each service needs its own Postgres database (`bytevault_users`,
`bytevault_products`, `bytevault_orders`) reachable at
`jdbc:postgresql://localhost:5434/...`.
