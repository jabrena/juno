package io.github.jabrena.juno.api.net.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

class JsonTest {
    private static final byte[] DOCUMENT = bytes("""
            {"users":[{"id":1},{"id":2147483648,"active":false,
                "name":"A\\n\\u00e9\\uD83D\\uDE00"}],"numbers":[-12,1.25e2,0.1,-0.0,1e400,1e-400,1.5e300],"nothing":null,
                "minimum":-9223372036854775808,"tooLarge":9223372036854775808,"text":"a\\"b"}""");

    @Test
    void walksMembersAndElements() {
        assertThat(type("users")).isEqualTo(Json.TYPE_ARRAY);
        assertThat(Json.arraySize(DOCUMENT, DOCUMENT.length, "users")).isEqualTo(2);
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "users[0].id")).isEqualTo(1);
        assertThat(type("users[1].active")).isEqualTo(Json.TYPE_BOOLEAN);
        assertThat(Json.getBool(DOCUMENT, DOCUMENT.length, "users[1].active")).isFalse();
        assertThat(type("nothing")).isEqualTo(Json.TYPE_NULL);
        assertThat(type("")).isEqualTo(Json.TYPE_OBJECT);
    }

    @Test
    void reportsMissingValuesApartFromMalformedPaths() {
        assertThat(type("missing")).isEqualTo(Json.TYPE_MISSING);
        assertThat(type("users[7]")).isEqualTo(Json.TYPE_MISSING);
        assertThat(type("nothing.x")).isEqualTo(Json.TYPE_MISSING);
        assertThat(type("users[01]")).isEqualTo(Json.TYPE_INVALID);
        assertThat(type("users[]")).isEqualTo(Json.TYPE_INVALID);
        assertThat(type("users[0")).isEqualTo(Json.TYPE_INVALID);
        assertThat(type("users.")).isEqualTo(Json.TYPE_INVALID);
        assertThat(type("users[0]x")).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void readsStrictlyIntegralNumbers() {
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "users[1].id")).isZero();
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "users[1].id")).isEqualTo(2147483648L);
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "numbers[0]")).isEqualTo(-12);
        assertThat(Json.getInt(DOCUMENT, DOCUMENT.length, "numbers[1]")).isZero();
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "minimum")).isEqualTo(Long.MIN_VALUE);
        assertThat(Json.getLong(DOCUMENT, DOCUMENT.length, "tooLarge")).isZero();
    }

    @Test
    void readsDoublesWithFractionsAndExponents() {
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[1]")).isEqualTo(125.0);
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[2]")).isEqualTo(0.1);
        assertThat(Double.doubleToRawLongBits(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[3]")))
                .isEqualTo(Double.doubleToRawLongBits(-0.0));
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[4]")).as("overflow").isZero();
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[5]")).as("underflow").isZero();
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "numbers[6]"))
                .as("the power of ten is built by repeated multiplication, so it rounds")
                .isCloseTo(1.5e300, withinPercentage(1e-12));
        assertThat(Json.getDouble(DOCUMENT, DOCUMENT.length, "users")).isZero();
    }

    @Test
    void decodesStringsToBoundedUtf8() {
        byte[] out = new byte[16];
        int written = Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", out, out.length);

        assertThat(written).isEqualTo(8);
        assertThat(new String(out, 0, written, StandardCharsets.UTF_8)).isEqualTo("A\né\uD83D\uDE00");
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", out, 4))
                .as("stops before a character that does not fit").isEqualTo(4);
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "users[1].name", out, 5)).isEqualTo(4);
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "text", out, out.length)).isEqualTo(3);
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "numbers", out, out.length)).isZero();
        assertThat(Json.getString(DOCUMENT, DOCUMENT.length, "text", out, 0)).isZero();
    }

    @Test
    void rejectsMalformedOrTruncatedDocuments() {
        assertThat(typeOf("{\"x\":[1,2", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":01}", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":[1,]}", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":1,}", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":\"\\q\"}", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":1} 2", "x")).isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("[".repeat(34) + "]".repeat(34), "")).as("nesting past 32").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("[".repeat(33) + "]".repeat(33), "")).isEqualTo(Json.TYPE_ARRAY);
        assertThat(Json.type(DOCUMENT, -1, "")).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void readsARootArray() {
        byte[] root = bytes("  [true, 3] ");

        assertThat(Json.type(root, root.length, "[0]")).isEqualTo(Json.TYPE_BOOLEAN);
        assertThat(Json.getBool(root, root.length, "[0]")).isTrue();
        assertThat(Json.getInt(root, root.length, "[1]")).isEqualTo(3);
        assertThat(Json.arraySize(root, root.length, "")).isEqualTo(2);
        assertThat(Json.arraySize(root, root.length, "[1]")).isEqualTo(-1);
    }

    @Test
    void decodesEveryShortEscapeAndAsciiUnicodeEscape() {
        byte[] buffer = bytes("{\"e\":\"\\b\\f\\r\\t\\/\\\\\\u0041\"}");
        byte[] out = new byte[16];

        int written = Json.getString(buffer, buffer.length, "e", out, out.length);

        assertThat(written).isEqualTo(7);
        assertThat(Arrays.copyOf(out, written)).containsExactly(8, 12, 13, 9, '/', '\\', 'A');
    }

    @Test
    void decodesThreeByteUtf8AndRejectsLoneSurrogates() {
        byte[] buffer = bytes("{\"e\":\"\\u20AC\",\"high\":\"\\uD83D\",\"low\":\"\\uDE00\",\"pair\":\"\\uD83D\\u0041\"}");
        byte[] out = new byte[8];

        assertThat(Json.getString(buffer, buffer.length, "e", out, out.length)).isEqualTo(3);
        assertThat(Arrays.copyOf(out, 3)).containsExactly((byte) 0xE2, (byte) 0x82, (byte) 0xAC);
        assertThat(Json.type(buffer, buffer.length, "high")).as("surrogates are scanned as text")
                .isEqualTo(Json.TYPE_STRING);
        assertThat(Json.getString(buffer, buffer.length, "high", out, out.length)).as("unpaired high").isZero();
        assertThat(Json.getString(buffer, buffer.length, "low", out, out.length)).as("unpaired low").isZero();
        assertThat(Json.getString(buffer, buffer.length, "pair", out, out.length)).as("high then non-low").isZero();
    }

    @Test
    void rejectsControlCharactersBadEscapesAndTruncatedStrings() {
        assertThat(typeOf("{\"x\":\"a\u0001\"}", "x")).as("raw control character").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":\"\\u12G4\"}", "x")).as("non-hex digit").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":\"\\u12\"}", "x")).as("short escape").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":\"abc", "x")).as("unterminated").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":\"a\\", "x")).as("escape at end").isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void readsSignedExponentsAndRejectsIncompleteNumbers() {
        assertThat(getDouble(bytes("{\"x\":1E-2}"), "x")).isCloseTo(0.01, withinPercentage(1e-12));
        assertThat(getDouble(bytes("{\"x\":1e+2}"), "x")).isEqualTo(100.0);
        assertThat(getDouble(bytes("{\"x\":-2.5e1}"), "x")).isEqualTo(-25.0);
        assertThat(typeOf("{\"x\":1.}", "x")).as("no digit after the point").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":1e}", "x")).as("no exponent digits").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":1e+}", "x")).as("no signed exponent digits").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":-}", "x")).as("sign only").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":-01}", "x")).as("leading zero after sign").isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void readsLongMantissasAndExtremeExponents() {
        byte[] buffer = bytes("{\"long\":0.1234567890123456789012345,\"big\":1e999999999,"
                + "\"small\":1e-999999999,\"wide\":123456789012345678901234567890,\"zero\":-0e5}");

        assertThat(getDouble(buffer, "long")).isCloseTo(0.12345678901234568, withinPercentage(1e-12));
        assertThat(getDouble(buffer, "big")).as("overflow exponent").isZero();
        assertThat(getDouble(buffer, "small")).as("underflow exponent").isZero();
        assertThat(getLong(buffer, "wide")).as("wider than 64 bits").isZero();
        assertThat(getDouble(buffer, "zero")).isZero();
    }

    @Test
    void locatesMembersAndElementsOrReportsTheirFailure() {
        byte[] buffer = bytes("{\"a\":1,\"b\":[true,{\"c\":null}],\"n\":{}}");

        assertThat(Json.type(buffer, buffer.length, "b[1].c")).isEqualTo(Json.TYPE_NULL);
        assertThat(Json.type(buffer, buffer.length, "b[1].d")).isEqualTo(Json.TYPE_MISSING);
        assertThat(Json.type(buffer, buffer.length, "a[0]")).as("index into a number").isEqualTo(Json.TYPE_MISSING);
        assertThat(Json.type(buffer, buffer.length, "a.x")).as("member of a number").isEqualTo(Json.TYPE_MISSING);
        assertThat(Json.type(buffer, buffer.length, "b[2147483648]")).isEqualTo(Json.TYPE_INVALID);
        assertThat(Json.type(buffer, buffer.length, "n.x")).isEqualTo(Json.TYPE_MISSING);
        assertThat(Json.arraySize(buffer, buffer.length, "n")).isEqualTo(-1);
        assertThat(Json.arraySize(buffer, buffer.length, "missing")).isEqualTo(-1);
    }

    @Test
    void rejectsMalformedMembersAndArrays() {
        assertThat(typeOf("{\"x\" 1}", "x")).as("no colon").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\"}", "x")).as("member without value").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{1:2}", "x")).as("unquoted name").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":1 \"y\":2}", "x")).as("missing comma").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("[1 2]", "[0]")).as("missing comma in array").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("[1,", "[0]")).as("truncated array").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":", "x")).as("truncated member").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":tru}", "x")).as("truncated literal").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":fals}", "x")).as("truncated false").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":nul}", "x")).as("truncated null").isEqualTo(Json.TYPE_INVALID);
        assertThat(typeOf("{\"x\":x}", "x")).as("unknown token").isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void acceptsWhitespaceAroundEveryToken() {
        byte[] buffer = bytes("\r\n{\t\"x\"\r\n:\t[ \r\n]\t,\"y\":false\r\n}\r\n");

        assertThat(Json.type(buffer, buffer.length, "x")).isEqualTo(Json.TYPE_ARRAY);
        assertThat(Json.arraySize(buffer, buffer.length, "x")).isZero();
        assertThat(Json.getBool(buffer, buffer.length, "y")).isFalse();
        assertThat(Json.type(buffer, buffer.length, "")).isEqualTo(Json.TYPE_OBJECT);
    }

    private static int type(String path) {
        return Json.type(DOCUMENT, DOCUMENT.length, path);
    }

    private static double getDouble(byte[] buffer, String path) {
        return Json.getDouble(buffer, buffer.length, path);
    }

    private static long getLong(byte[] buffer, String path) {
        return Json.getLong(buffer, buffer.length, path);
    }

    private static int typeOf(String document, String path) {
        byte[] buffer = bytes(document);
        return Json.type(buffer, buffer.length, path);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
