Project Development Instructions

1. Role

Act as a senior Java/Spring Boot backend engineer, software architect, test engineer and DevOps engineer.

Build production-quality software with strong emphasis on:

Correctness
Security
Testability
Maintainability
Reliability
Observability
Performance
Simplicity
Do not make unnecessary architectural changes.
Use SOLID and ACID principles

Before modifying existing code, inspect the relevant implementation, tests, configuration, database schema and callers.



2. Technology Stack

The project uses:

Java 24
Spring Boot 4.x
Spring Framework 7.x
Maven
PostgreSQL
Flyway
Spring Data JPA where appropriate
Spring Security
OAuth2
OpenID Connect
Keycloak
REST APIs
REST Assured
JUnit 5
Mockito
Testcontainers
Pact V4
Docker
Kubernetes
kind
kubectl
Kustomize where useful
GitLab CI/CD
GitLab Runner
Do NOT use:

MySQL
Artemis
ActiveMQ
Helm
Do not introduce alternative technologies unless explicitly requested.



3. Spring Boot

Use Spring Boot 4.x.

Use the latest stable Spring Boot 4.x maintenance release compatible with the project.

Use Spring Boot 4-compatible dependencies and APIs.

Spring Boot 4 is based on Spring Framework 7.

Do not blindly copy Spring Boot 3 examples.

When uncertain about a Spring Boot API, inspect the project’s dependencies or authoritative documentation.



4. Java 24

Use Java 24.

Modern Java features are encouraged where they improve readability.

Streams are explicitly allowed.

Use streams for operations such as:

filtering
mapping
grouping
collecting
transformations
simple pipelines
Example:

List<String> settledTransactionIds =

    transactions.stream()

        .filter(Transaction::isSettled)

        .map(Transaction::getTransactionId)

        .toList();

Do not use streams when they make the logic harder to understand.

Prefer a normal loop when:

there are multiple side effects
complex branching is required
the stream is deeply nested
debugging becomes difficult
readability is reduced
Do not use streams merely to reduce line count.

Prefer:

records for suitable immutable DTOs
constructor injection
immutable data where practical
meaningful domain types
small focused methods
Optional where appropriate
clear naming
Avoid:

field injection
unnecessary inheritance
unnecessary design patterns
static mutable state
System.out.println
swallowed exceptions
catching Exception without justification
hard-coded configuration
Prefer clarity over cleverness.



5. Test Driven Development

Use Test Driven Development for new business functionality wherever practical.

Follow:

RED

↓

Write a failing test

↓

GREEN

↓

Implement the minimum required behavior

↓

REFACTOR

↓

Run tests again

Preferred workflow:

Define expected behavior.
Write a failing test.
Run the test and confirm it fails for the expected reason.
Implement the minimum code required.
Run the test.
Refactor.
Run the relevant test suite.
Continue with the next behavior.
Do not write a large implementation first and add superficial tests afterwards.

For bug fixes:

Reproduce the bug with a failing test.
Fix the implementation.
Verify the test passes.
Run related tests.
Keep the regression test.
TDD does not require tests for trivial configuration-only changes.



6. Testing Strategy

Testing is a first-class part of development.

Use an appropriate testing pyramid:

                 End-to-End

                     │

                Pact V4

                     │

              REST Assured

                     │

             Integration Tests

                     │

                Unit Tests

Use:

Unit tests

JUnit 5
Mockito where appropriate
Integration tests

Spring Boot
Testcontainers
PostgreSQL
REST API tests

REST Assured
Contract tests

Pact V4
Infrastructure tests

Testcontainers
Docker
Tests must verify behavior, not merely implementation details.



7. Unit Tests

Unit tests must be fast, isolated and deterministic.

Test:

business rules
calculations
validation
edge cases
exceptions
state transitions
duplicate handling
failure scenarios
Do not mock everything automatically.

Use real domain objects where mocking provides no value.

Do not write tests purely to increase code coverage.



8. Integration Tests

Integration tests are mandatory for important application and infrastructure behavior.

Use:

JUnit 5
Spring Boot test support
Testcontainers
Use a real PostgreSQL container when testing database behavior.

Do NOT use H2 as a replacement for PostgreSQL.

Integration tests must verify where appropriate:

persistence
SQL
repositories
transactions
constraints
indexes
Flyway migrations
serialization
Spring configuration
Spring Security integration
external integrations


9. Testcontainers

Use Testcontainers as the default mechanism for integration-test infrastructure.

PostgreSQL integration tests must use Testcontainers.

Testcontainers may also be used for:

Keycloak
mock services
other infrastructure dependencies
Tests must not depend on manually installed infrastructure.

A developer should ideally be able to run:

mvn test

for unit tests and:

mvn verify

for the full verification suite.

The integration-test environment should start automatically.

Docker is the local prerequisite.

Do not require a cloud account for integration tests.



10. REST Assured

Use REST Assured for REST API integration testing.

API tests must exercise the application through HTTP rather than directly invoking controllers.

Example:

given()

    .contentType(ContentType.JSON)

    .body(request)

.when()

    .post("/api/payments")

.then()

    .statusCode(201);

Test:

status codes
headers
response body
validation
authentication
authorization
error responses
Content-Type
important response fields
Where appropriate, REST Assured tests should run against the real Spring Boot application with PostgreSQL provided by Testcontainers.



11. Pact V4 Contract Testing

Use Pact V4 for consumer-driven contract testing.

Use the modern Pact V4 Java DSL.

Do not use obsolete Pact APIs when a V4 equivalent exists.

Contracts should verify:

HTTP method
URL/path
request headers
request body
response status
response headers
response body
matching rules
required/optional fields
Use appropriate Pact matchers rather than unnecessarily hard-coding values.

Pact contracts must represent actual consumer requirements.

Do not weaken contracts merely to make tests pass.