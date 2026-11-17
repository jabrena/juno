package io.github.jabrena.juno.hardware.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SPIKE #285: JSON writer into a caller buffer")
class JsonWriterTest {

    @Test
    void writesNestedObjectsAndArraysWithCommas() {
        byte[] body = new byte[128];
        JsonWriter json = new JsonWriter(body);

        json.beginObject()
                .name("uptime_ms").value(123456789L)
                .name("ok").value(true)
                .name("tags").beginArray().value("a").value(-7).nullValue().beginObject().endObject().endArray()
                .name("min").value(Long.MIN_VALUE)
                .endObject();

        assertThat(text(body, json)).isEqualTo(
                "{\"uptime_ms\":123456789,\"ok\":true,\"tags\":[\"a\",-7,null,{}],\"min\":-9223372036854775808}");
        assertThat(json.overflowed()).isFalse();
    }

    @Test
    void escapesQuotesBackslashesAndControlCharacters() {
        byte[] body = new byte[64];
        JsonWriter json = new JsonWriter(body);

        json.beginObject().name("a\"b").value("c\\d\n\u0001").endObject();

        assertThat(text(body, json)).isEqualTo("{\"a\\\"b\":\"c\\\\d\\u000a\\u0001\"}");
        assertThat(Json.view(text(body, json)).get("a\"b").isMissing()).isTrue();
    }

    @Test
    void producesJsonThatTheViewReadsBack() {
        byte[] body = new byte[64];
        JsonWriter json = new JsonWriter(body);

        json.beginArray().beginObject().name("uuid").value("x-1").name("n").value(0).endObject().endArray();

        JsonView first = Json.view(body, json.length()).get(0);
        assertThat(first.get("n").asInt()).isZero();
        assertThat(first.get("uuid").isString()).isTrue();
    }

    @Test
    void dropsWhatDoesNotFitAndReportsIt() {
        byte[] body = new byte[8];
        JsonWriter json = new JsonWriter(body);

        json.beginObject().name("long-name").value(1).endObject();

        assertThat(json.length()).isEqualTo(8);
        assertThat(json.overflowed()).isTrue();
        assertThat(json.reset().beginArray().endArray().length()).isEqualTo(2);
        assertThat(json.overflowed()).isFalse();
    }

    @Test
    void rejectsANameOutsideAnObjectAndAnUnbalancedClose() {
        JsonWriter json = new JsonWriter(new byte[16]);

        assertThat(json.beginArray().name("x").overflowed()).isTrue();
        assertThat(json.reset().endObject().overflowed()).isTrue();
        assertThat(json.reset().beginObject().name("a").endObject().overflowed()).isTrue();
    }

    private static String text(byte[] body, JsonWriter json) {
        return new String(body, 0, json.length(), StandardCharsets.UTF_8);
    }
}
