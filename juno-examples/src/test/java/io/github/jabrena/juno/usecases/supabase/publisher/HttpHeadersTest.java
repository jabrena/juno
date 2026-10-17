package io.github.jabrena.juno.usecases.supabase.publisher;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpHeadersTest {

    @Test
    void writesEveryNameValuePairAsAHeaderLine() {
        byte[] buffer = new byte[128];

        HttpHeaders headers = new HttpHeaders(buffer, buffer.length)
                .add("apikey", "sb_publishable_test")
                .add("Content-Type", "application/json")
                .add("Prefer", "return=representation");

        assertThat(new String(buffer, 0, headers.length(), StandardCharsets.US_ASCII)).isEqualTo(
                "apikey: sb_publishable_test\r\nContent-Type: application/json\r\nPrefer: return=representation\r\n");
    }

    @Test
    void rejectsNonAsciiTextAndAFullBuffer() {
        byte[] buffer = new byte[16];

        assertThat(new HttpHeaders(buffer, buffer.length).add("x", "café").length()).isEqualTo(-1);
        assertThat(new HttpHeaders(buffer, buffer.length).add("apikey", "a-very-long-key").add("a", "b").length())
                .isEqualTo(-1);
    }
}
