# Design: `POST /api/public/echo`

Status: proposed — contract only, no implementation yet.

## Purpose

A small, dependency-free public endpoint that exercises the full HTTP + Bean
Validation path of this contract-first service (routing, request-body
validation, response serialization) without touching a database, Keycloak, or
any external system. It sits alongside `GET /api/public/hello` under the
`Public` tag.

## Behavior

- `POST /api/public/echo`
- Request body: `EchoRequest { "message": "<string>" }`
- Response body (`200`): `EchoResponse`:
  - `message` — the request `message`, trimmed of leading/trailing whitespace
  - `length` — `int32`, the character length of the **trimmed** message
    (`String#length()`, i.e. UTF-16 code units — see Unicode note below)
  - `shout` — the trimmed message, uppercased
- No authentication (`security: []`, same as `/api/public/hello`); already
  covered by the existing `.requestMatchers("/api/public/**").permitAll()`
  rule in `SecurityConfig`, so no security-config change is needed.
- No persistence: no entity, no Flyway migration, no repository.

## Request validation

Enforced entirely by Bean Validation annotations generated from the OpenAPI
schema (`EchoRequest.message`), **not** by hand-written code:

- `maxLength: 200` → `@Size(max = 200)`, measured **before** trimming.
- Must contain at least one non-whitespace character → `pattern:
  '^[\s\S]*\S[\s\S]*$'` → `@Pattern(regexp = "^[\\s\\S]*\\S[\\s\\S]*$")`.
  This single pattern rejects both the empty string and whitespace-only
  strings, because it requires at least one `\S` character to appear
  anywhere in the value. `minLength: 1` is also declared (redundant with the
  pattern, but cheap and gives a clearer failed-constraint name if someone
  later loosens the pattern).
- **Why not `minLength` alone**: `minLength: 1` only rejects the empty
  string; a value of `"   "` satisfies `minLength: 1` but must still be
  rejected because it is entirely whitespace. The `pattern` constraint above
  is what actually catches whitespace-only input — this was verified by
  generating sources (`mvn generate-sources`) and inspecting the emitted
  `@Pattern` on `EchoRequest.message`.
- Confirmed generated interface (`target/generated-sources/openapi/.../PublicApi.java`):
  `EchoResponse postPublicEcho(@Valid @RequestBody EchoRequest echoRequest)`.
  The `@Valid` triggers Bean Validation before the controller method body
  ever runs, so an invalid request never reaches application code.

### Shape of the `400` response

This project has **no `@ControllerAdvice`** and does not set
`spring.mvc.problemdetails.enabled` (grepped the whole repo to confirm — see
`application.yml`, `application-docker.yml`, `application-test.yml`: neither
key is present, and `spring.mvc.problemdetails.enabled` defaults to `false`
in Spring Boot 3.4). So when `@Valid` fails on `EchoRequest`, Spring throws
`MethodArgumentNotValidException`, which falls through to Spring Boot's
default `BasicErrorController` / `DefaultErrorAttributes`, **not** an RFC
7807 `ProblemDetail`. With this project's default `server.error.*` settings
(`include-message: never`, `include-binding-errors: never`,
`include-stacktrace: never`), the body is:

```json
{
  "timestamp": "2026-09-28T18:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/api/public/echo"
}
```

There is **no field-level violation list and no `message` key** — do not
write REST Assured assertions expecting one (e.g. no `$.errors[0].field`).
Assert on `status == 400` and, if useful, `$.path == "/api/public/echo"`.
This shape is documented in the OpenAPI diff as `components.responses.ValidationError`
(a loose `type: object` schema with `timestamp` / `status` / `error` / `path`)
so it's discoverable from the contract, not just this doc.

If a future feature wants field-level validation errors, that requires
adding a `@ControllerAdvice`/`ProblemDetail` handler project-wide — out of
scope here; flagging it as a known gap rather than working around it for
just this endpoint.

## Edge cases the test-writer agent must cover

HTTP-layer (REST Assured, real Bean Validation via `@Valid`):

| # | Input | Expected |
|---|-------|----------|
| 1 | `message` absent from body | `400` |
| 2 | `message: ""` (blank) | `400` |
| 3 | `message: "   "` (whitespace-only: spaces/tabs/newlines) | `400` |
| 4 | `message` exactly 200 characters (pre-trim) | `200`, echoed/shouted correctly |
| 5 | `message` exactly 201 characters (pre-trim) | `400` |
| 6 | Unicode input, e.g. `"héllo wörld 日本語 🎉"` | `200`; `length` reflects `String#length()` (UTF-16 code units — see note below); `shout` uppercases what Java's `toUpperCase` can uppercase |
| 7 | Already-uppercase input, e.g. `"ALREADY LOUD"` | `200`; `shout` equals the trimmed input unchanged |
| 8 | Leading/trailing whitespace, e.g. `"  hi  "` | `200`; `message` is `"hi"` (trimmed), `length` is `2`, `shout` is `"HI"` — trimming must happen before both length and shout are computed |
| 9 | No `Authorization` header at all | `200` (endpoint is public) — mirrors the existing `publicEndpointIsOpen()` test style in `ControllerSecurityTest` |

Unit-layer (`EchoService`, see below) — pure transformation only, since
input validity is guaranteed by `@Valid` before the service is ever called
in production. Cover cases 4, 6, 7, 8 above directly against the service
method (no Spring context, no MockMvc), plus:

- Interior whitespace is preserved (only leading/trailing is trimmed), e.g.
  `"  a  b  "` → message `"a  b"`.
- A message with mixed-width Unicode (e.g. containing an emoji outside the
  BMP) still round-trips through trim/uppercase without throwing.

Do **not** unit-test blank/whitespace-only/201-char rejection against
`EchoService` — that behavior lives in the generated `@Valid` annotations on
`EchoRequest`, not in the service, and belongs in the REST Assured suite
against the real HTTP endpoint (or, if desired, a narrow test asserting the
Bean Validation constraint annotations exist — but that's covered by
exercising the endpoint).

### Known, accepted limitations (do not treat as bugs to fix here)

- **Code units vs. code points**: `length` and the `@Size`/`maxLength`
  validation both use Java's `String#length()` (UTF-16 code units). An
  astral-plane character (e.g. many emoji) counts as 2 toward both the
  200-char limit and the reported `length`, even though it is one visual
  character / one JSON-Schema-sense "character". This is standard Java
  string-length behavior throughout this codebase (nothing here uses
  `codePointCount`) and is intentionally not special-cased.
- **Unicode whitespace**: both the `@Pattern` (`\s` = `[ \t\n\x0B\f\r]` in
  default Java regex, no `UNICODE_CHARACTER_CLASS`) and `String#trim()`
  (strips only code points `<= U+0020`) treat only ASCII control/space
  characters as whitespace. A message consisting solely of, say, U+00A0
  (non-breaking space) or U+3000 (ideographic space) is **not** rejected by
  validation and is **not** trimmed — it passes through as ordinary
  "non-blank" content. This is consistent between the validation and the
  trimming behavior (both key off `<= U+0020`), so it will not surprise
  testers who check one against the other, but it's worth a comment in the
  test file. Not a target for this design — call it out if a future
  requirement needs full Unicode-blank detection (`Character.isWhitespace`).

## Implementation guidance (for the developer agent — not implemented here)

Add a small, pure, Spring-managed service:

```java
package za.co.tsa.oidcdemo.service;

// EchoService — stateless, no I/O. Assumes its input already satisfies the
// OpenAPI/Bean Validation constraints on EchoRequest.message (non-blank
// after trim, <= 200 chars before trim); it does not re-validate.
public class EchoService {
    // EchoResponse echo(String message)
    //   - trims message
    //   - shout = trimmed.toUpperCase(Locale.ROOT)   // NOT toUpperCase() —
    //     avoid the default-locale (e.g. Turkish dotless-ı) uppercasing bug
    //   - length = trimmed.length()
    //   - returns new EchoResponse(trimmed, length, shout)
}
```

- `EchoService` must **not** depend on Spring MVC types (`HttpServletRequest`,
  `@RequestBody`, etc.) — that's what makes it unit-testable without a
  `MockMvc`/`@SpringBootTest` context, per `CLAUDE.md` §7 (fast, isolated,
  deterministic unit tests).
- `PublicController` gains a constructor-injected `EchoService` field
  (same pattern as `UserController`'s `AuthenticatedUserService`) and a
  `postPublicEcho(EchoRequest)` override that delegates to it. Not written
  here — out of scope for the design agent.
- `toUpperCase(Locale.ROOT)` is a hard requirement, not a suggestion: the
  no-arg `String#toUpperCase()` uses the JVM default locale, which is
  environment-dependent and can silently produce wrong output (Turkish
  locale's dotless ı/İ handling is the canonical example). Flagging this now
  so it isn't missed during implementation or introduced as a latent bug.

## Data / schema implications

None. No entity, no repository, no Flyway migration, no new table. Purely
transient request/response processing.

## Testing pyramid layers that apply

Per `CLAUDE.md` §6:

- **Unit tests** — `EchoServiceTest` (new, once the service exists): the
  transformation edge cases listed above (trim, length, shout, Unicode,
  already-uppercase, interior-whitespace preservation). Fast, no Spring
  context.
- **REST Assured** — extend the existing HTTP-level suite (this repo's
  pattern is `ControllerSecurityTest`/`OpenApiDocsTest` using MockMvc-style
  `@SpringBootTest` + `@AutoConfigureMockMvc`, but per `CLAUDE.md` §10 the
  API-behavior edge cases in the table above should go through real HTTP via
  REST Assured, exercising the real Bean Validation and the real
  `PublicController`). Covers: all validation edge cases (1–5), the
  happy-path transformation edge cases (6–8) confirmed end-to-end, and the
  no-auth-required case (9).
- **Integration tests (Testcontainers)** — not applicable. No database, no
  Keycloak dependency for this endpoint (it's under `/api/public/**`, and
  `security: []` in the contract).
- **Pact V4** — not applicable. No external consumer of this API exists in
  this repository (consistent with the rest of the project — there is no
  existing Pact usage to extend).

## Assumptions made

1. **Response field names** — `message` / `length` / `shout` were chosen to
   match the existing single-purpose, lower-camel-case, no-abbreviation
   style (`MessageResponse.message`, `UserInfoResponse.authenticationMethod`).
   No existing naming precedent forced a specific choice here; these read
   naturally against the stated behavior.
2. **`operationId: postPublicEcho`** — follows the existing
   `get<Resource>` pattern (`getPublicHello`, `getCurrentUser`,
   `getAdminStats`) with the HTTP verb swapped to `post`.
3. **HTTP status for success is `200`, not `201`** — this endpoint doesn't
   create a resource; it's an action/computation endpoint, consistent with
   `GET /api/public/hello` also returning `200` for a similarly
   resource-less response.
4. **`maxLength: 200` is pre-trim**, per the task statement; `length` in the
   response is **post-trim**, also per the task statement. These are
   different numbers by design when the input has leading/trailing
   whitespace (edge case 8 above makes this explicit for the test-writer).
5. **Whitespace-only rejection is handled at the schema/Bean Validation
   layer** (via `pattern`), not deferred to the service layer, per the
   task's stated preference for that option when a workable pattern exists.
