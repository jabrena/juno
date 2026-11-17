package io.github.jabrena.juno.api.hardware.net.http.json;

/** SPIKE (#285): the fixed response documents {@link JsonAsIs} and {@link JsonToBe} both read. */
final class Documents {
    static final String WEATHER = "{\"current\":{\"temperature_2m\":21.4,\"relative_humidity_2m\":63,"
            + "\"wind_speed_10m\":12.5,\"weather_code\":3,\"is_day\":1},\"utc_offset_seconds\":3600}";
    static final String PUZZLE = "{\"game\":{\"pgn\":\"e4 e5\"},\"puzzle\":{\"fen\":\"8/8/8 w - - 0 1\","
            + "\"rating\":1712,\"plays\":42,\"solution\":[\"e2e4\",\"e7e5\",\"g1f3\",\"b8c6\"],\"themes\":[\"fork\"]}}";
    static final String UUIDS = "[{\"uuid\":\"0b9f-01\",\"title\":\"a\"},{\"uuid\":\"0b9f-02\",\"title\":\"b\"},"
            + "{\"uuid\":\"0b9f-03\",\"title\":\"c\"},{\"uuid\":\"0b9f-04\",\"title\":\"d\"}]";

    private Documents() {
    }
}
