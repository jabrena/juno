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

@DisplayName("JSON views over a String")
class JsonStringTest {
    private static final String DOCUMENT = """
            {"users":[{"id":1},{"id":2147483648,"active":false,
                "name":"A\\n\\u00e9\\uD83D\\uDE00"}],"numbers":[-12,1.25e2,0.1,-0.0,1e400,1e-400,1.5e300],"nothing":null,
                "minimum":-9223372036854775808,"tooLarge":9223372036854775808,"text":"a\\"b",
                "current":{"temperature_2m":13.5,"time":"2026-11-17T00:15"}}""";
    private static final String[] PATHS = {"", "users", "users[0].id", "users[1].id", "users[1].active",
        "users[1].name", "users[7]", "numbers", "numbers[0]", "numbers[1]", "numbers[2]", "numbers[3]", "numbers[4]",
        "numbers[5]", "numbers[6]", "nothing", "nothing.x", "minimum", "tooLarge", "text", "missing",
        "current.temperature_2m", "current.time"};

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
        JsonValue fromText = JsonPaths.at(json, path);
        JsonValue fromBytes = JsonPaths.at(bytes, bytes.length, path);

        assertThat(fromText.type()).isEqualTo(fromBytes.type());
        assertThat(fromText.asInt()).isEqualTo(fromBytes.asInt());
        assertThat(fromText.asLong()).isEqualTo(fromBytes.asLong());
        assertThat(fromText.asDouble()).isEqualTo(fromBytes.asDouble());
        assertThat(fromText.asBoolean()).isEqualTo(fromBytes.asBoolean());
        assertThat(fromText.size()).isEqualTo(fromBytes.size());
        byte[] textOut = new byte[16];
        byte[] bytesOut = new byte[16];
        assertThat(fromText.copyString(textOut)).isEqualTo(fromBytes.copyString(bytesOut));
        assertThat(textOut).isEqualTo(bytesOut);
    }

    @Test
    @DisplayName("a value is read straight from a response body held in a String")
    void readsAResponseBody() {
        JsonValue body = Json.parse("{\"current\":{\"temperature_2m\":13.5,\"time\":\"2026-11-17T00:15\"},\"ok\":true}");
        JsonValue current = body.get("current");

        assertThat(current.get("temperature_2m").asDouble()).isEqualTo(13.5);
        assertThat(current.get("temperature_2m").toString()).isEqualTo("13.5");
        assertThat(current.get("temperature_2m").asString()).isNull();
        assertThat(current.get("time").asString()).isEqualTo("2026-11-17T00:15");
        assertThat(current.asString()).isNull();
        assertThat(body.get("missing").asString()).isNull();
        assertThat(body.get("ok").asBoolean()).isTrue();
        assertThat(body.get("ok").asString()).isNull();
        assertThat(body.get("ok").toString()).isEqualTo("true");
        assertThat(current.toString()).isEqualTo("{\"temperature_2m\":13.5,\"time\":\"2026-11-17T00:15\"}");
        assertThat(current.get("time").toString()).isEqualTo("\"2026-11-17T00:15\"");
        assertThat(body.get("missing").toString()).isEmpty();
        assertThat(current.type()).isEqualTo(Json.TYPE_OBJECT);
    }

    @Test
    @DisplayName("an escaped String value decodes to the same UTF-8 bytes as from a buffer")
    void decodesEscapes() {
        byte[] out = new byte[16];

        int written = Json.parse("{\"name\":\"A\\n\\u00e9\\uD83D\\uDE00\"}").get("name").copyString(out);

        assertThat(new String(out, 0, written, StandardCharsets.UTF_8)).isEqualTo("A\né😀");
        assertThat(Json.parse("{\"name\":\"x\"}").get("name").copyString(out, 0)).isZero();
        assertThat(Json.parse("{\"name\":\"a\\\"b\"}").get("name").asString()).isEqualTo("a\"b");
        assertThat(Json.parse("{\"name\":\"a\\\"b\"}").get("name").toString()).isEqualTo("\"a\\\"b\"");
    }

    @Test
    @DisplayName("asString decodes escapes to the UTF-8 bytes a runtime String holds")
    void asStringDecodesToUtf8Bytes() {
        String decoded = Json.parse("{\"name\":\"A\\n\\u00e9\\uD83D\\uDE00\"}").get("name").asString();

        assertThat(decoded).isNotNull();
        assertThat(decoded.getBytes(StandardCharsets.ISO_8859_1))
                .isEqualTo("A\né😀".getBytes(StandardCharsets.UTF_8));
    }
}
