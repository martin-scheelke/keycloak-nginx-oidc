Feature: OIDC login through Keycloak behind the NGINX reverse proxy

  The microservice delegates authentication to Keycloak using the OpenID Connect
  authorization-code flow. Visiting a protected page while unauthenticated must bounce the
  browser through Keycloak and back, and realm roles must drive access to the admin API.

  Background:
    Given the demo stack is reachable

  Scenario: An unauthenticated visitor to a protected page is redirected to Keycloak
    When I open the protected page "/secured"
    Then my browser is on the Keycloak login page

  Scenario: A user with the admin role signs in and reaches every endpoint
    Given I open the protected page "/secured"
    When I sign in as "demo" with password "demo"
    Then I land back on the application at "/secured"
    And the secured page greets "demo"
    And calling "/api/user/me" returns HTTP 200 and lists the roles "admin,user"
    And calling "/api/admin/stats" returns HTTP 200

  Scenario: A user without the admin role is forbidden from the admin endpoint
    Given I open the protected page "/secured"
    When I sign in as "alice" with password "alice"
    Then calling "/api/user/me" returns HTTP 200 and lists the roles "user"
    And calling "/api/admin/stats" returns HTTP 403

  Scenario: Logging out clears the session
    Given I open the protected page "/secured"
    And I sign in as "demo" with password "demo"
    When I log out
    And I open the protected page "/secured"
    Then my browser is on the Keycloak login page
