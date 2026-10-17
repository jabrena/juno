package io.github.jabrena.juno.usecases.supabase.commons;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseClientTest {

    @Test
    void buildsASelectWithEncodedColumnsFiltersAndLimit() {
        Fixture fixture = new Fixture();

        fixture.client.from("sensor_readings")
                .select("temperature,humidity")
                .eq("room", "lab 1")
                .limit(10);

        assertThat(fixture.client.ready()).isTrue();
        assertThat(text(fixture.path, fixture.client.pathLength()))
                .isEqualTo("/rest/v1/sensor_readings?select=temperature%2Chumidity&room=eq.lab%201&limit=10");
    }

    @Test
    void buildsASelectFilterFromACallerOwnedAsciiValue() {
        Fixture fixture = new Fixture();
        byte[] cutoff = "2026-10-31T23:30:00Z".getBytes(StandardCharsets.US_ASCII);

        fixture.client.from("sensor-data")
                .select("ts,temp1,temp2,temp3")
                .filter("ts", "gte", cutoff, cutoff.length)
                .limit(60);

        assertThat(fixture.client.ready()).isTrue();
        assertThat(text(fixture.path, fixture.client.pathLength()))
                .isEqualTo("/rest/v1/sensor-data?select=ts%2Ctemp1%2Ctemp2%2Ctemp3"
                        + "&ts=gte.2026-10-31T23%3A30%3A00Z&limit=60");
    }

    @Test
    void buildsAKeysetPageOrderedByIdAfterACallerOwnedCursor() {
        Fixture fixture = new Fixture();
        byte[] cursor = "42".getBytes(StandardCharsets.US_ASCII);

        fixture.client.from("sensor-data")
                .select("id,temp1,temp2,temp3")
                .filter("id", "gt", cursor, cursor.length)
                .order("id")
                .limit(60);

        assertThat(fixture.client.ready()).isTrue();
        assertThat(text(fixture.path, fixture.client.pathLength()))
                .isEqualTo("/rest/v1/sensor-data?select=id%2Ctemp1%2Ctemp2%2Ctemp3"
                        + "&id=gt.42&order=id.asc&limit=60");
    }

    @Test
    void buildsInsertAndUpdateBodiesWithoutReplacingCallerBuffers() {
        Fixture fixture = new Fixture();

        fixture.client.from("sensor_readings").insert("{\"temperature\":23}");

        assertThat(fixture.client.ready()).isTrue();
        assertThat(text(fixture.body, fixture.client.requestBodyLength())).isEqualTo("{\"temperature\":23}");

        fixture.client.from("sensor_readings").update("{\"temperature\":24}").eq("id", "7");

        assertThat(fixture.client.ready()).isTrue();
        assertThat(text(fixture.path, fixture.client.pathLength()))
                .isEqualTo("/rest/v1/sensor_readings?id=eq.7");
        assertThat(text(fixture.body, fixture.client.requestBodyLength())).isEqualTo("{\"temperature\":24}");
    }

    @Test
    void preparesApiKeyPreferAndOptionalAuthorizationHeaders() {
        Fixture fixture = new Fixture();

        fixture.client.authorize("user.jwt");

        assertThat(text(fixture.requestHeaders, fixture.client.requestHeadersLength()))
                .isEqualTo("apikey: sb_publishable_test\r\n"
                        + "Prefer: return=representation\r\n"
                        + "Authorization: Bearer user.jwt\r\n");
    }

    @Test
    void requestsAnExactSelectCountWithoutLeakingThatPreferenceToTheNextRequest() {
        Fixture fixture = new Fixture();

        fixture.client.from("sensor_readings").select("id").countExact();

        assertThat(text(fixture.requestHeaders, fixture.client.requestHeadersLength()))
                .isEqualTo("apikey: sb_publishable_test\r\n"
                        + "Prefer: return=representation\r\n"
                        + "Prefer: count=exact\r\n");

        fixture.client.from("sensor_readings").select("id");

        assertThat(text(fixture.requestHeaders, fixture.client.requestHeadersLength()))
                .isEqualTo("apikey: sb_publishable_test\r\nPrefer: return=representation\r\n");
    }

    @Test
    void rejectsUnsafeMutationAndBufferOverflow() {
        Fixture fixture = new Fixture();

        assertThat(fixture.client.from("sensor_readings").delete().ready()).isFalse();

        byte[] shortPath = new byte[8];
        SupabaseClient shortClient = client(shortPath, shortPath.length, new byte[64], 64, new byte[32], 32);
        shortClient.from("sensor_readings").select("id");

        assertThat(shortClient.ready()).isFalse();
    }

    @Test
    void responseExposesReusableStorageAndStatus() {
        byte[] responseBody = new byte[16];
        byte[] responseHeaders = new byte[16];
        int[] status = {201, 7};
        SupabaseResponse response = new SupabaseResponse(responseBody, 16, responseHeaders, 16, status);

        response.completed(5);

        assertThat(response.bodyBuffer()).isSameAs(responseBody);
        assertThat(response.headersBuffer()).isSameAs(responseHeaders);
        assertThat(response.bodyLength()).isEqualTo(5);
        assertThat(response.headersLength()).isEqualTo(7);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.successful()).isTrue();
    }

    private static SupabaseClient client(
            byte[] path, int pathCapacity, byte[] requestHeaders, int requestHeadersCapacity,
            byte[] body, int bodyCapacity) {
        byte[] responseBody = new byte[128];
        byte[] responseHeaders = new byte[64];
        int[] status = new int[2];
        SupabaseResponse response = new SupabaseResponse(responseBody, responseBody.length,
                responseHeaders, responseHeaders.length, status);
        return new SupabaseClient("project.supabase.co", "sb_publishable_test",
                path, pathCapacity, requestHeaders, requestHeadersCapacity, body, bodyCapacity, response);
    }

    private static String text(byte[] buffer, int length) {
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }

    private static final class Fixture {
        private final byte[] path = new byte[256];
        private final byte[] requestHeaders = new byte[256];
        private final byte[] body = new byte[128];
        private final SupabaseClient client = client(
                path, path.length, requestHeaders, requestHeaders.length, body, body.length);
    }
}
