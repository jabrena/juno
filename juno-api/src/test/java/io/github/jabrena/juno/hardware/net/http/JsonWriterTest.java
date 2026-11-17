package io.github.jabrena.juno.hardware.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

@DisplayName("JSON writer into a caller buffer")
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

        assertThat(text(body, json)).isEqualTo("{\"a\\\"b\":\"c\\\\d\\n\\u0001\"}");
        assertThat(Json.parse(text(body, json)).get("a\"b").isMissing()).isTrue();
    }

    @Test
    void producesJsonThatTheViewReadsBack() {
        byte[] body = new byte[64];
        JsonWriter json = new JsonWriter(body);

        json.beginArray().beginObject().name("uuid").value("x-1").name("n").value(0).endObject().endArray();

        JsonValue first = Json.parse(body, json.length()).get(0);
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

    @ParameterizedTest(name = "{0} is written as {1}")
    @CsvSource({"21.4, 21.4", "100.0, 100.0", "0.1, 0.1", "0.001, 0.001", "-2.5, -2.5", "1.5E300, 1.5E300",
        "1.0E-5, 1.0E-5", "1.23456789E8, 1.23456789E8", "1234567.0, 1234567.0", "0.0, 0.0", "-0.0, -0.0",
        "0.3333333333333333, 0.333333333333333", "9.9999999999999999, 10.0", "2.5E-200, 2.5E-200",
        "1.0E308, 1.0E308"})
    @DisplayName("Writes a double with up to 15 significant digits, laid out as Double.toString does")
    void writesDoubles(double value, String expected) {
        byte[] body = new byte[32];
        JsonWriter json = new JsonWriter(body);

        json.value(value);

        assertThat(text(body, json)).isEqualTo(expected);
        assertThat(Json.parse(text(body, json)).asDouble()).isCloseTo(value, withinPercentage(1e-12));
    }

    @Test
    @DisplayName("Refuses NaN and the infinities, which JSON cannot write")
    void refusesNanAndInfinities() {
        JsonWriter json = new JsonWriter(new byte[16]);

        assertThat(json.beginArray().value(Double.NaN).overflowed()).isTrue();
        assertThat(json.reset().value(Double.POSITIVE_INFINITY).overflowed()).isTrue();
        assertThat(json.reset().value(Double.NEGATIVE_INFINITY).length()).isZero();
    }

    @Test
    @DisplayName("Writes text from a char buffer, escaped like a String, and copies the body into a String")
    void writesCharBuffersAndCopiesTheBodyIntoAString() {
        JsonWriter json = new JsonWriter(new byte[64]);
        char[] title = {'H', 'i', '\t', '"', 'x'};

        json.beginObject().name("title").value(title, 4).name("n").value(-7).endObject();

        assertThat(json.toString()).isEqualTo("{\"title\":\"Hi\\t\\\"\",\"n\":-7}");
        assertThat(Json.parse(json.toString()).get("title").asString()).isEqualTo("Hi\t\"");
    }

    private static String text(byte[] body, JsonWriter json) {
        return new String(body, 0, json.length(), StandardCharsets.UTF_8);
    }
}
