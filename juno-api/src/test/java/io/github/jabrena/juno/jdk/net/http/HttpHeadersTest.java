package io.github.jabrena.juno.jdk.net.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpHeadersTest {
    private static final String BLOCK = "Content-Type: application/json\r\n"
            + "Content-Length: 42\r\n"
            + "X-Padded:   spaced value  \r\n"
            + "Location: /next\r\n"
            + "X-Negative: -7\r\n"
            + "X-Text: abc\r\n"
            + "X-Empty:\r\n"
            + "\r\n";

    @Test
    @DisplayName("a header is found by its name in any case, without surrounding spaces")
    void findsTheFirstValue() {
        HttpHeaders headers = headers(BLOCK, BLOCK.length());

        assertThat(headers.firstValue("content-type").get()).isEqualTo("application/json");
        assertThat(headers.firstValue("LOCATION").get()).isEqualTo("/next");
        assertThat(headers.firstValue("X-Padded").get()).isEqualTo("spaced value");
        assertThat(headers.firstValue("X-Empty").get()).isEmpty();
    }

    @Test
    @DisplayName("a missing header, or one only a prefix of the name matches, is empty")
    void missingHeaderIsEmpty() {
        HttpHeaders headers = headers(BLOCK, BLOCK.length());

        assertThat(headers.firstValue("Server").isEmpty()).isTrue();
        assertThat(headers.firstValue("Content").isEmpty()).isTrue();
        assertThat(headers.firstValueAsLong("Server").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("only the bytes the request wrote are read: a header past them is not found")
    void readsOnlyTheBytesWritten() {
        int firstLine = "Content-Type: application/json\r\n".length();
        HttpHeaders headers = headers(BLOCK, firstLine);

        assertThat(headers.firstValue("Content-Type").get()).isEqualTo("application/json");
        assertThat(headers.firstValue("Content-Length").isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a numeric header reads as a long, and a non-numeric one throws as the JDK does")
    void readsNumbers() {
        HttpHeaders headers = headers(BLOCK, BLOCK.length());

        assertThat(headers.firstValueAsLong("content-length").getAsLong()).isEqualTo(42L);
        assertThat(headers.firstValueAsLong("X-Negative").getAsLong()).isEqualTo(-7L);
        assertThatThrownBy(() -> headers.firstValueAsLong("X-Text")).isInstanceOf(NumberFormatException.class);
        assertThatThrownBy(() -> headers.firstValueAsLong("X-Empty")).isInstanceOf(NumberFormatException.class);
    }

    private static HttpHeaders headers(String text, int written) {
        byte[] block = new byte[HttpClient.RESPONSE_HEADERS_CAPACITY];
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, block, 0, bytes.length);
        return new HttpHeaders(block, new int[] {200, written});
    }
}
