package io.github.jabrena.juno.jdk.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientTest {

    @Test
    @DisplayName("the byte-array handler answers with the client's one reused response and its whole buffer")
    void byteArrayHandlerReusesTheClientsResponse() {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse.BodyHandler<byte[]> handler = HttpResponse.BodyHandlers.ofByteArray();

        HttpResponse<byte[]> first = handler.respond(client, 200, 0);
        HttpResponse<byte[]> second = handler.respond(client, 404, 0);

        assertThat(second).isSameAs(first);
        assertThat(second.statusCode()).isEqualTo(404);
        assertThat(second.body()).hasSize(HttpClient.BODY_CAPACITY).containsOnly(0);
    }

    @Test
    @DisplayName("the String handler is one shared object, and answers with a new response of the bytes received")
    void stringHandlerAnswersWithItsOwnResponse() {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse.BodyHandler<String> handler = HttpResponse.BodyHandlers.ofString();

        HttpResponse<String> first = handler.respond(client, 200, 0);
        HttpResponse<String> second = handler.respond(client, 503, 0);

        assertThat(HttpResponse.BodyHandlers.ofString()).isSameAs(handler);
        assertThat(second).isNotSameAs(first);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(second.statusCode()).isEqualTo(503);
        assertThat(second.body()).isEmpty();
    }

    @Test
    @DisplayName("a String body holds exactly the bytes received, one character per byte, without decoding them")
    void textKeepsTheBytesReceived() {
        byte[] buffer = new byte[HttpClient.BODY_CAPACITY];
        byte[] json = "{\"temperature_2m\":13.5}".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(json, 0, buffer, 0, json.length);

        assertThat(HttpClient.text(buffer, json.length)).isEqualTo("{\"temperature_2m\":13.5}");
        assertThat(HttpClient.text(buffer, 0)).isEmpty();
        assertThat(HttpClient.text(new byte[] {(byte) 0xC3, (byte) 0xA9}, 2)).isEqualTo("Ã©");
    }
}
