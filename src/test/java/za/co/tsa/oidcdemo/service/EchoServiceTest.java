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
 * deliberately NOT tested here -- that behavior is covered at the HTTP layer instead
 * (see {@link za.co.tsa.oidcdemo.web.EchoEndpointTest}).
 */
class EchoServiceTest {

    private final EchoService service = new EchoService();

    // ---- edge case 4: exactly 200 characters (pre-trim, no trimming needed) -------------

    /**
     * Feeds a 200-character message (the OpenAPI schema's {@code maxLength}, so the largest
     * input the service is ever contractually asked to handle) that has no leading/trailing
     * whitespace at all.
     *
     * <p>Verifies: the message passes through unchanged, {@code length} equals exactly 200
     * (not 199 or 201 — an off-by-one in trimming or length calculation would show up here),
     * and {@code shout} is the same string fully uppercased. This is the "biggest valid input,
     * nothing to trim" case — it pins down that the service does not do anything unexpected
     * (truncation, re-validation, etc.) at the boundary the contract allows.
     */
    @Test
    void exactlyTwoHundredCharacterMessageIsEchoedAndShoutedInFull() {
        String message = "a".repeat(200);

        EchoResponse response = service.echo(message);

        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getLength()).isEqualTo(200);
        assertThat(response.getShout()).isEqualTo("A".repeat(200));
    }

    // ---- edge case 6: Unicode, including an astral-plane (surrogate-pair) character ------

    /**
     * Feeds a message mixing accented Latin ({@code é}, {@code ö}), CJK ({@code 日本語}), and an
     * emoji ({@code 🎉}, which lies outside the Basic Multilingual Plane and is therefore
     * represented as a two-{@code char} UTF-16 surrogate pair, not one).
     *
     * <p>Verifies: the message is returned byte-for-byte / char-for-char unchanged (no
     * normalization or mangling), {@code length} matches Java's own {@code String#length()}
     * on the input (i.e. the service reports UTF-16 code units, the same unit the 200-char
     * OpenAPI {@code maxLength} constraint is measured in — this test pins that consistency
     * down rather than asserting a hand-picked number), and {@code shout} matches what
     * {@code String#toUpperCase(Locale.ROOT)} itself would produce on that input — i.e. the
     * service must not do its own ad-hoc uppercasing logic, it must delegate to the JDK.
     */
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

    /**
     * Feeds a message containing a surrogate-pair emoji sandwiched between plain ASCII words.
     * This is a narrower, more paranoid companion to the test above: where that test checks
     * the *values* are correct, this one specifically checks that trimming and uppercasing a
     * string containing a split multi-{@code char} code point does not throw (e.g. an
     * off-by-one substring/trim implementation could otherwise slice through the middle of a
     * surrogate pair and either throw or corrupt the string).
     *
     * <p>Verifies: no exception is thrown, and the message/length come back exactly as
     * expected once the call *has* succeeded.
     */
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

    /**
     * Feeds a message that is already fully uppercase.
     *
     * <p>Verifies: uppercasing an already-uppercase string is idempotent — {@code shout}
     * equals the input unchanged, not doubled, mangled, or (for certain locales/characters)
     * subtly altered. Also a basic sanity check that {@code length} counts what a human would
     * expect (12) when there's no Unicode subtlety involved, as a plain baseline alongside the
     * Unicode-specific tests above.
     */
    @Test
    void alreadyUppercaseMessageShoutEqualsTrimmedInput() {
        String message = "ALREADY LOUD";

        EchoResponse response = service.echo(message);

        assertThat(response.getMessage()).isEqualTo("ALREADY LOUD");
        assertThat(response.getShout()).isEqualTo("ALREADY LOUD");
        assertThat(response.getLength()).isEqualTo(12);
    }

    // ---- edge case 8: leading/trailing whitespace is trimmed before length AND shout -----

    /**
     * Feeds {@code "  hi  "} — two leading spaces, the word "hi", two trailing spaces.
     *
     * <p>Verifies: trimming happens <em>before</em> {@code length} and {@code shout} are
     * computed, not after or not at all. If trimming were skipped, {@code length} would be 6
     * instead of 2 and {@code shout} would be {@code "  HI  "} instead of {@code "HI"}. This is
     * the core "does the service actually trim, and in the right order" test — the design doc
     * is explicit that {@code length} is measured post-trim, so this is the test that would
     * fail first if that ordering were ever broken.
     */
    @Test
    void leadingAndTrailingWhitespaceIsTrimmedBeforeLengthAndShoutAreComputed() {
        EchoResponse response = service.echo("  hi  ");

        assertThat(response.getMessage()).isEqualTo("hi");
        assertThat(response.getLength()).isEqualTo(2);
        assertThat(response.getShout()).isEqualTo("HI");
    }

    // ---- interior whitespace must be preserved, only leading/trailing trimmed ------------

    /**
     * Feeds {@code "  a  b  "} — leading/trailing spaces around two words separated by
     * <em>interior</em> double-spacing.
     *
     * <p>Verifies: only the outer whitespace is stripped ({@code "a  b"}, length 4) — the
     * double space between "a" and "b" survives untouched. This guards against an overly
     * aggressive implementation that collapses or strips all whitespace (e.g. via
     * {@code replaceAll("\\s+", "")} or a regex trim) instead of calling the equivalent of
     * {@code String#trim()}, which only affects the ends of the string.
     */
    @Test
    void interiorWhitespaceIsPreservedOnlyLeadingAndTrailingIsTrimmed() {
        EchoResponse response = service.echo("  a  b  ");

        assertThat(response.getMessage()).isEqualTo("a  b");
        assertThat(response.getLength()).isEqualTo(4);
        assertThat(response.getShout()).isEqualTo("A  B");
    }

    // ---- Locale.ROOT correctness: shout must not use the JVM default locale --------------

    /**
     * Regression guard for a specific, well-known Java footgun: {@code String#toUpperCase()}
     * (no-arg) uses the <em>JVM default locale</em>, which is environment-dependent. Under a
     * Turkish locale, lowercase {@code 'i'} uppercases to {@code 'İ'} (dotted capital I,
     * U+0130) instead of plain ASCII {@code 'I'} — code that works perfectly in CI/dev (usually
     * an English-ish locale) can silently misbehave in production if the JVM's default locale
     * ever differs.
     *
     * <p>This test deliberately flips the JVM default locale to {@code tr-TR} for the duration
     * of the call, then restores it in a {@code finally} block so it can't leak into other
     * tests. It asserts that {@code shout} is still plain {@code "I"} regardless — proving the
     * implementation uses {@code toUpperCase(Locale.ROOT)} explicitly rather than relying on
     * whatever locale happens to be active.
     */
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
