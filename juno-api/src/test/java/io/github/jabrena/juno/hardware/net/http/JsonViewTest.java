package io.github.jabrena.juno.hardware.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JSON view with JEP 540 names")
class JsonViewTest {
    private static final String PUZZLE = """
            {"puzzle":{"fen":"8/8 w","rating":1500,"solution":["e2e4","e7e5", "g1f3"],"themes":[]},
             "weird.name":{"a[0]":true},"nothing":null,"score":-1.25e2,"big":2147483648}""";

    @Test
    void navigatesMembersAndElementsWithoutPathStrings() {
        JsonView puzzle = Json.view(PUZZLE).get("puzzle");
        byte[] uci = new byte[8];

        assertThat(puzzle.get("rating").asInt()).isEqualTo(1500);
        assertThat(puzzle.get("solution").size()).isEqualTo(3);
        int length = puzzle.get("solution").get(2).copyString(uci);
        assertThat(new String(Arrays.copyOf(uci, length), StandardCharsets.UTF_8)).isEqualTo("g1f3");
    }

    @Test
    void matchesMemberNamesThatPathsCannotAddress() {
        assertThat(Json.view(PUZZLE).get("weird.name").get("a[0]").asBoolean()).isTrue();
    }

    @Test
    void reportsTypesAndCountsMembers() {
        JsonView root = Json.view(PUZZLE);

        assertThat(root.type()).isEqualTo(Json.TYPE_OBJECT);
        assertThat(root.size()).isEqualTo(5);
        assertThat(root.get("puzzle").get("themes").size()).isZero();
        assertThat(root.get("nothing").isNull()).isTrue();
        assertThat(root.get("score").isNumber()).isTrue();
        assertThat(root.get("puzzle").get("fen").isString()).isTrue();
        assertThat(root.get("score").asDouble()).isEqualTo(-125.0);
    }

    @Test
    void missingStepsStayMissingAndConvertToZeroValues() {
        JsonView missing = Json.view(PUZZLE).get("puzzle").get("absent").get(3).get("deeper");

        assertThat(missing.isMissing()).isTrue();
        assertThat(missing.asInt()).isZero();
        assertThat(missing.size()).isEqualTo(-1);
        assertThat(missing.copyString(new byte[4])).isZero();
        assertThat(Json.view(PUZZLE).get(0).isMissing()).isTrue();
        assertThat(Json.view(PUZZLE).get("puzzle").get("solution").get(-1).isMissing()).isTrue();
    }

    @Test
    void keepsTheGettersFailureRules() {
        assertThat(Json.view(PUZZLE).get("big").asInt()).isZero();
        assertThat(Json.view(PUZZLE).get("big").asLong()).isEqualTo(2147483648L);
        assertThat(Json.view(PUZZLE).get("score").asInt()).isZero();
        assertThat(Json.view(PUZZLE).get("puzzle").asInt()).isZero();
    }

    @Test
    void anInvalidDocumentGivesInvalidViewsAllTheWayDown() {
        JsonView invalid = Json.view("{\"x\":[1,]}");

        assertThat(invalid.type()).isEqualTo(Json.TYPE_INVALID);
        assertThat(invalid.get("x").get(0).type()).isEqualTo(Json.TYPE_INVALID);
    }

    @Test
    void readsAByteBuffer() {
        byte[] buffer = new byte[32];
        byte[] json = "[{\"uuid\":\"a1\"}] trailing".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(json, 0, buffer, 0, 15);
        byte[] uuid = new byte[4];

        assertThat(Json.view(buffer, 15).get(0).get("uuid").copyString(uuid)).isEqualTo(2);
        assertThat(Json.view(buffer, 16).type()).isEqualTo(Json.TYPE_INVALID);
    }
}
