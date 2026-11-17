package io.github.jabrena.juno.hardware.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Minimal JSON reader over a String")
class JsonStringTest {
    private static final String DOCUMENT = """
            {"users":[{"id":1},{"id":2147483648,"active":false,
                "name":"A\\n\\u00e9\\uD83D\\uDE00"}],"numbers":[-12,1.25e2,0.1,-0.0,1e400,1e-400,1.5e300],"nothing":null,
                "minimum":-9223372036854775808,"tooLarge":9223372036854775808,"text":"a\\"b",
                "current":{"temperature_2m":13.5,"time":"2026-11-17T00:15"}}""";
    private static final String[] PATHS = {"", "users", "users[0].id", "users[1].id", "users[1].active",
        "users[1].name", "users[7]", "numbers", "numbers[0]", "numbers[1]", "numbers[2]", "numbers[3]", "numbers[4]",
        "numbers[5]", "numbers[6]", "nothing", "nothing.x", "minimum", "tooLarge", "text", "missing",
        "current.temperature_2m", "current.time", "users[01]", "users[]", "users[0", "users.", "users[0]x"};

    static Stream<Arguments> documentsAndPaths() {
        Stream<Arguments> document = Stream.of(PATHS).map(path -> arguments(DOCUMENT, path));
        Stream<Arguments> others = Stream.of(
                arguments("[1,2,3]", "[2]"),
                arguments("[1,2,3]", ""),
                arguments("  {\"a\" : [ true , false ] }  ", "a[1]"),
                arguments("{\"a\":1,}", "a"),
                arguments("{\"a\":", "a"),
                arguments("", ""),
                arguments("{\"a\":\"unterminated}", "a"),
                arguments("[01]", "[0]"),
                arguments("{\"deep\":[[[[1]]]]}", "deep[0][0][0][0]"));
        return Stream.concat(document, others);
    }

    @ParameterizedTest(name = "{1} in {0}")
    @MethodSource("documentsAndPaths")
    @DisplayName("every method answers for a String exactly as for the same bytes")
    void answersAsForBytes(String json, String path) {
        byte[] bytes = json.getBytes(StandardCharsets.US_ASCII);
        int length = bytes.length;

        assertThat(Json.type(json, path)).isEqualTo(Json.type(bytes, length, path));
        assertThat(Json.getInt(json, path)).isEqualTo(Json.getInt(bytes, length, path));
        assertThat(Json.getLong(json, path)).isEqualTo(Json.getLong(bytes, length, path));
        assertThat(Json.getDouble(json, path)).isEqualTo(Json.getDouble(bytes, length, path));
        assertThat(Json.getBool(json, path)).isEqualTo(Json.getBool(bytes, length, path));
        assertThat(Json.arraySize(json, path)).isEqualTo(Json.arraySize(bytes, length, path));
        byte[] fromText = new byte[16];
        byte[] fromBytes = new byte[16];
        assertThat(Json.getString(json, path, fromText, fromText.length))
                .isEqualTo(Json.getString(bytes, length, path, fromBytes, fromBytes.length));
        assertThat(fromText).isEqualTo(fromBytes);
    }

    @Test
    @DisplayName("a value is read straight from a response body held in a String")
    void readsAResponseBody() {
        String body = "{\"current\":{\"temperature_2m\":13.5,\"time\":\"2026-11-17T00:15\"},\"ok\":true}";

        assertThat(Json.getDouble(body, "current.temperature_2m")).isEqualTo(13.5);
        assertThat(Json.getString(body, "current.temperature_2m")).isEqualTo("13.5");
        assertThat(Json.getString(body, "current.time")).isEqualTo("2026-11-17T00:15");
        assertThat(Json.getString(body, "current")).isNull();
        assertThat(Json.getString(body, "missing")).isNull();
        assertThat(Json.getBool(body, "ok")).isTrue();
        assertThat(Json.type(body, "current")).isEqualTo(Json.TYPE_OBJECT);
    }

    @Test
    @DisplayName("an escaped String value decodes to the same UTF-8 bytes as from a buffer")
    void decodesEscapes() {
        byte[] out = new byte[16];

        int written = Json.getString("{\"name\":\"A\\n\\u00e9\\uD83D\\uDE00\"}", "name", out, out.length);

        assertThat(new String(out, 0, written, StandardCharsets.UTF_8)).isEqualTo("A\né😀");
        assertThat(Json.getString("{\"name\":\"x\"}", "name", out, 0)).isZero();
    }
}
