package io.github.jabrena.juno.api.net.http;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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

    private static int type(String path) {
        return Json.type(DOCUMENT, DOCUMENT.length, path);
    }

    private static int typeOf(String document, String path) {
        byte[] buffer = bytes(document);
        return Json.type(buffer, buffer.length, path);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
