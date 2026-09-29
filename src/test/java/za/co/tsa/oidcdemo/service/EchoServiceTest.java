package za.co.tsa.oidcdemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import za.co.tsa.oidcdemo.api.model.EchoResponse;

/**
 * Pure unit tests for {@link EchoService} per docs/design/public-echo.md.
 *
 * <p>No Spring context: {@code EchoService} is a plain, stateless transformation and is
 * exercised directly. Per the design doc, input validity (non-blank after trim, <= 200 chars
 * before trim) is guaranteed by Bean Validation on {@code EchoRequest.message} before the
 * service is ever invoked in production, so blank/whitespace-only/201-char rejection is
 * deliberately NOT tested here -- that behavior is covered at the HTTP layer instead.
 */
class EchoServiceTest {

    private final EchoService service = new EchoService();

    // ---- edge case 4: exactly 200 characters (pre-trim, no trimming needed) -------------

    @Test
    void exactlyTwoHundredCharacterMessageIsEchoedAndShoutedInFull() {
        String message = "a".repeat(200);

        EchoResponse response = service.echo(message);

        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getLength()).isEqualTo(200);
        assertThat(response.getShout()).isEqualTo("A".repeat(200));
    }

    // ---- edge case 6: Unicode, including an astral-plane (surrogate-pair) character ------

    @Test
    void unicodeMessageRoundTripsUsingUtf16CodeUnitLength() {
        String message = "héllo wörld 日本語 🎉";

        EchoResponse response = service.echo(message);

        assertThat(response.getMessage()).isEqualTo(message);
        // String#length() counts UTF-16 code units; the design doc calls out that the emoji
        // (outside the BMP) counts as 2 toward this length, same as toward maxLength.
        assertThat(response.getLength()).isEqualTo(message.length());
        assertThat(response.getShout()).isEqualTo(message.toUpperCase(java.util.Locale.ROOT));
    }

    @Test
    void astralPlaneCharacterDoesNotThrowDuringTrimOrUppercase() {
        // U+1F389 PARTY POPPER, represented as a surrogate pair in UTF-16.
        String astral = "🎉";
        String message = "party " + astral + " time";

        assertThatCode(() -> service.echo(message)).doesNotThrowAnyException();

        EchoResponse response = service.echo(message);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getLength()).isEqualTo(message.length());
    }

    // ---- edge case 7: already-uppercase input is unchanged by shout ----------------------

    @Test
    void alreadyUppercaseMessageShoutEqualsTrimmedInput() {
        String message = "ALREADY LOUD";

        EchoResponse response = service.echo(message);

        assertThat(response.getMessage()).isEqualTo("ALREADY LOUD");
        assertThat(response.getShout()).isEqualTo("ALREADY LOUD");
        assertThat(response.getLength()).isEqualTo(12);
    }

    // ---- edge case 8: leading/trailing whitespace is trimmed before length AND shout -----

    @Test
    void leadingAndTrailingWhitespaceIsTrimmedBeforeLengthAndShoutAreComputed() {
        EchoResponse response = service.echo("  hi  ");

        assertThat(response.getMessage()).isEqualTo("hi");
        assertThat(response.getLength()).isEqualTo(2);
        assertThat(response.getShout()).isEqualTo("HI");
    }

    // ---- interior whitespace must be preserved, only leading/trailing trimmed ------------

    @Test
    void interiorWhitespaceIsPreservedOnlyLeadingAndTrailingIsTrimmed() {
        EchoResponse response = service.echo("  a  b  ");

        assertThat(response.getMessage()).isEqualTo("a  b");
        assertThat(response.getLength()).isEqualTo(4);
        assertThat(response.getShout()).isEqualTo("A  B");
    }

    // ---- Locale.ROOT correctness: shout must not use the JVM default locale --------------

    @Test
    void shoutUppercasesUsingLocaleRootNotDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            // Turkish locale's dotless-i uppercasing is the canonical example where
            // no-arg toUpperCase() silently diverges from Locale.ROOT: under Turkish,
            // 'i' uppercases to 'İ' (dotted capital I, U+0130), not plain 'I'.
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));

            EchoResponse response = service.echo("i");

            assertThat(response.getShout())
                    .as("shout must use Locale.ROOT regardless of the JVM default locale")
                    .isEqualTo("I");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
