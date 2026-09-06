# keycloak-nginx-oidc

A small Spring Boot REST microservice that delegates authentication to **Keycloak** over
**OpenID Connect**, sits behind an **NGINX** reverse proxy, and is packaged with **Docker
Compose**.

The same service accepts two kinds of credential against the same Keycloak realm:

| Caller | Mechanism | Spring Security |
| --- | --- | --- |
| Browser | OIDC authorization-code flow → session cookie | `oauth2Login` |
| API client / script | `Authorization: Bearer <access-token>` (JWT) | `oauth2ResourceServer` (JWT) |

Roles come from the Keycloak realm (`realm_access.roles`) and are mapped to Spring
authorities (`ROLE_user`, `ROLE_admin`).

---

## Architecture

```
                         :8080 (published)
  Browser / curl  ─────────────►  NGINX  ─────────────►  app  (Spring Boot :8080)
        │                          │  \
        │  redirect to login       │   └──────────────►  keycloak :8080
        └──────────────────────────┘        /realms /resources /admin /js
```

* **NGINX** is the only published port. It routes `/realms`, `/resources`, `/admin`, `/js`,
  `/robots.txt` to Keycloak and everything else to the app.
* Every party — the browser, the app's back channel, and Selenium — uses the single URL
  `http://host.docker.internal:8080`, so the token `iss` claim matches everywhere.
  `host.docker.internal` resolves on the host automatically with Docker Desktop; the `app`
  container reaches it via `extra_hosts: host-gateway`.

### Endpoints

| Method & path | Access | Notes |
| --- | --- | --- |
| `GET /api/public/hello` | anonymous | smoke test |
| `GET /api/user/me` | any authenticated principal | subject, username, email, roles, scopes |
| `GET /api/admin/stats` | realm role `admin` | returns `403` for non-admins |
| `GET /secured` | authenticated (browser) | HTML page; unauthenticated → redirect to Keycloak |
| `GET /swagger-ui.html` | anonymous | OpenAPI UI |
| `GET /openapi.yaml` | anonymous | the hand-authored OpenAPI contract (static file) |
| `GET /actuator/health` | anonymous | liveness/readiness |

This service is **contract-first**: [`openapi/openapi.yaml`](openapi/openapi.yaml) is
hand-authored and is the single source of truth. At build time the
`openapi-generator-maven-plugin` generates the Spring `*Api` interfaces and model classes
into `za.co.tsa.oidcdemo.api` (under `target/generated-sources`, not committed); the
controllers `implements PublicApi / UserApi / AdminApi`. Swagger UI (`/swagger-ui.html`)
renders that same YAML, served verbatim as a static file — nothing is generated at runtime,
so the docs cannot drift from the contract.

### Keycloak realm (`docker/keycloak/realm-demo.json`)

* realm `demo`; realm roles `user`, `admin`
* confidential client `demo-app` / secret `demo-app-secret`; standard flow + direct-access grant
* protocol mappers: realm roles into the access **and** ID token as `realm_access.roles`;
  `demo-app` added to the token audience
* users: **`demo` / `demo`** (roles `user` + `admin`), **`alice` / `alice`** (role `user`)

---

## Prerequisites

### Everyone

* **Docker** with Compose v2 (`docker compose version` ≥ 2.x)
* **Google Chrome** — only for the Selenium/Cucumber end-to-end tests (`-Pe2e`). The matching
  driver is downloaded automatically by Selenium Manager.
* A free TCP port **8080**.

The Maven wrapper (`./mvnw`) is committed, so a system Maven install is not required. A local
**JDK 21** is only needed to run the test suites outside Docker; the application image builds
Java inside the container.

### Windows 11 (PowerShell, `winget`)

```powershell
winget install --id Docker.DockerDesktop -e
winget install --id EclipseAdoptium.Temurin.21.JDK -e
winget install --id Google.Chrome -e
winget install --id Git.Git -e
```

Start Docker Desktop once and let it finish initialising. `host.docker.internal` works out of
the box.

### macOS (Homebrew)

```bash
brew install --cask docker            # or: brew install colima docker docker-compose
brew install --cask temurin@21
brew install --cask google-chrome
```

Launch Docker Desktop (or `colima start`). With Docker Desktop, `host.docker.internal`
resolves on the host automatically.

### Linux (Debian/Ubuntu)

```bash
# Docker Engine + Compose plugin — see https://docs.docker.com/engine/install/
sudo apt-get install -y docker.io docker-compose-plugin openjdk-21-jdk google-chrome-stable
sudo usermod -aG docker "$USER"   # log out/in afterwards
```

Docker Engine (no Desktop) does **not** provide `host.docker.internal` on the host, so add:

```bash
echo "127.0.0.1 host.docker.internal" | sudo tee -a /etc/hosts
```

(The `app` container still gets it via `extra_hosts` in `docker-compose.yml`.)

---

## Run the stack

```bash
cp .env.example .env          # optional: change admin password / client secret
docker compose up -d --build
```

Wait until `docker compose ps` shows `app` as **healthy** (Keycloak import + Spring start take
~30–60 s on first run), then:

| What | URL | Credentials |
| --- | --- | --- |
| Landing page | http://host.docker.internal:8080/ | log in as `demo` / `demo` |
| Protected page | http://host.docker.internal:8080/secured | — |
| Swagger UI | http://host.docker.internal:8080/swagger-ui.html | "Authorize" → `keycloak-oidc` |
| Keycloak admin console | http://host.docker.internal:8080/admin/ | `admin` / `admin` |

`http://localhost:8080` also reaches the app; the login redirect will switch the host to
`host.docker.internal`. Prefer `host.docker.internal` for a clean single-origin session.

### Call the API with a Bearer token

```bash
TOKEN=$(curl -s http://host.docker.internal:8080/realms/demo/protocol/openid-connect/token \
  -d grant_type=password -d client_id=demo-app -d client_secret=demo-app-secret \
  -d username=demo -d password=demo -d scope=openid | jq -r .access_token)

curl -s -H "Authorization: Bearer $TOKEN" http://host.docker.internal:8080/api/user/me | jq
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://host.docker.internal:8080/api/admin/stats           # 200 for demo, 403 for alice
```

### Tear down

```bash
docker compose down -v
```

---

## Tests

| Command | Scope | Needs |
| --- | --- | --- |
| `./mvnw test` | unit + Spring-slice security tests (mock auth) + OpenAPI contract validation | JDK 21 |
| `./mvnw verify` | the above **+** `KeycloakContainerIT` (real Keycloak via Testcontainers, REST Assured) | JDK 21, Docker |
| `docker compose up -d --build && ./mvnw verify -Pe2e` | the above **+** Selenium/Cucumber browser flow through NGINX | JDK 21, Docker, Chrome |

The layers:

* **Unit** — `KeycloakRealmRoleConverterTest`, `AuthenticatedUserServiceTest`: claim → authority
  mapping and identity assembly, no Spring.
* **Slice** — `ControllerSecurityTest`: every authorization rule, driven with
  `spring-security-test` post-processors (`jwt()`, `oidcLogin()`); `OpenApiDocsTest`.
* **Integration** — `KeycloakContainerIT`: starts Keycloak with the real realm import, fetches
  real tokens via the direct-access grant, and exercises the resource server over HTTP with
  REST Assured (`demo` → 200/200, `alice` → 200/403, bad token → 401).
* **End-to-end** — `src/test/resources/features/oidc-login.feature` + `OidcLoginSteps`:
  headless Chrome visits `/secured`, is redirected to Keycloak, signs in, is redirected back,
  and the realm role drives `/api/admin/stats`. Also covers logout.

### Changing the API

Edit `openapi/openapi.yaml`, then run any build (`./mvnw generate-sources` regenerates just
the interfaces/models). If you change an operation's signature, the controller that
`implements` the interface stops compiling until you update it — the contract drives the code.
`OpenApiSpecValidationTest` fails the build if the YAML is not valid OpenAPI.

---

## Local development without Docker

Run only Keycloak in a container and the app from your IDE:

```bash
docker compose up -d keycloak nginx
./mvnw spring-boot:run          # uses http://localhost:8080/realms/demo as issuer
```

---

## Demo simplifications (do not copy to production)

* **HTTP only** — no TLS anywhere.
* **CSRF disabled** — so the static page and `curl` need no token. Keep CSRF for the
  session part in production (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), exempting only
  the stateless `/api/**` routes.
* **Client secret committed** in `docker/keycloak/realm-demo.json` and `.env.example`.
* **Keycloak dev mode** (`start-dev`) with in-memory storage — all data resets on restart, and
  the realm is re-imported each boot.
