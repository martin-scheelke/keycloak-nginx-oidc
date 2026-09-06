package za.co.tsa.oidcdemo.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

/**
 * A protected, server-rendered page (outside {@code /api/**}) used to demonstrate and test the
 * full browser redirect flow: an unauthenticated GET here triggers {@code oauth2Login}, which
 * 302-redirects to Keycloak's authorization endpoint.
 */
@Controller
public class SecuredViewController {

    @GetMapping(value = "/secured", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String secured(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            // e.g. reached with a Bearer token rather than a browser session
            return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                    + "<title>Secured</title></head><body><h1 id=\"secured-heading\">Secured area</h1>"
                    + "<p>Authenticated, but not via a browser session.</p></body></html>";
        }
        String name = HtmlUtils.htmlEscape(user.getPreferredUsername());
        String roles = HtmlUtils.htmlEscape(String.valueOf(user.getClaimAsMap("realm_access")));
        return """
                <!doctype html>
                <html lang="en">
                <head><meta charset="utf-8"><title>Secured</title></head>
                <body>
                  <h1 id="secured-heading">Secured area</h1>
                  <p>Signed in as <strong id="username">%s</strong>.</p>
                  <p id="realm-access">%s</p>
                  <p><a href="/">Back to home</a></p>
                </body>
                </html>
                """.formatted(name, roles);
    }
}
