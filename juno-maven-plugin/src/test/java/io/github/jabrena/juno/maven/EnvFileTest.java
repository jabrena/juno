package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Environment file parsing")
class EnvFileTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("Reads key-value lines and skips comments, blanks and malformed lines")
    void shouldReadKeyValueLinesAndSkipCommentsBlanksAndMalformedLines() {
        // Given
        EnvFile file = new EnvFile(directory.resolve(".env"));

        // When
        var entries = file.parse("""
                # Wi-Fi credentials
                WIFI_SSID = home

                WIFI_PASSWORD=a=b
                not a pair
                WIFI_SSID=office
                """.lines().toList());

        // Then
        assertThat(entries).containsExactly(
                Map.entry("WIFI_SSID", "office"),
                Map.entry("WIFI_PASSWORD", "a=b"));
    }

    @Test
    @DisplayName("Reports a missing file as absent")
    void shouldReportAMissingFileAsAbsent() {
        // Given
        EnvFile file = new EnvFile(directory.resolve("missing.env"));

        // When
        boolean exists = file.exists();

        // Then
        assertThat(exists).isFalse();
    }
}
