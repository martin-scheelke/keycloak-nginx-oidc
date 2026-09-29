package za.co.tsa.oidcdemo.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import za.co.tsa.oidcdemo.api.model.EchoResponse;

/**
 * Pure, stateless transformation backing {@code POST /api/public/echo} per
 * {@code docs/design/public-echo.md}.
 *
 * <p>Assumes its input already satisfies the OpenAPI/Bean Validation constraints on
 * {@code EchoRequest.message} (non-blank after trim, at most 200 characters before trim) and does
 * not re-validate; that validation happens via {@code @Valid} on the generated {@code EchoRequest}
 * before this service is ever invoked in production.
 */
@Service
public class EchoService {

    public EchoResponse echo(String message) {
        String trimmed = message.trim();
        String shout = trimmed.toUpperCase(Locale.ROOT);
        return new EchoResponse(trimmed, trimmed.length(), shout);
    }
}
