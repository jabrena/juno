package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SNTP server configuration")
class SntpServerTest {
    @Test
    @DisplayName("Defaults to pool.ntp.org on port 123")
    void shouldDefaultToThePoolNtpServer() {
        // When
        SntpServer server = SntpServer.from(Map.of());

        // Then
        assertThat(server).isEqualTo(new SntpServer("pool.ntp.org", 123));
    }

    @Test
    @DisplayName("Reads JUNO_SNTP_HOST and JUNO_SNTP_PORT")
    void shouldReadTheHostAndPortFromTheEnvironment() {
        // When
        SntpServer server = SntpServer.from(Map.of("JUNO_SNTP_HOST", "time.google.com", "JUNO_SNTP_PORT", "1123"));

        // Then
        assertThat(server).isEqualTo(new SntpServer("time.google.com", 1123));
    }

    @Test
    @DisplayName("Rejects a host that is a URL")
    void shouldRejectAHostThatIsAUrl() {
        // When & Then
        assertThatThrownBy(() -> SntpServer.from(Map.of("JUNO_SNTP_HOST", "ntp://pool.ntp.org")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_SNTP_HOST");
    }

    @Test
    @DisplayName("Rejects a port that is not a number in 1..65535")
    void shouldRejectAnInvalidPort() {
        // When & Then
        assertThatThrownBy(() -> SntpServer.from(Map.of("JUNO_SNTP_PORT", "70000")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_SNTP_PORT");
        assertThatThrownBy(() -> SntpServer.from(Map.of("JUNO_SNTP_PORT", "ntp")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JUNO_SNTP_PORT");
    }
}
