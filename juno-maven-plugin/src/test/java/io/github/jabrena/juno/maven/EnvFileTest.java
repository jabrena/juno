package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvFileTest {
    @TempDir
    Path directory;

    @Test
    void readsKeyValueLinesAndSkipsCommentsBlanksAndMalformedLines() {
        EnvFile file = new EnvFile(directory.resolve(".env"));

        var entries = file.parse("""
                # Wi-Fi credentials
                WIFI_SSID = home

                WIFI_PASSWORD=a=b
                not a pair
                WIFI_SSID=office
                """.lines().toList());

        assertThat(entries).containsExactly(
                Map.entry("WIFI_SSID", "office"),
                Map.entry("WIFI_PASSWORD", "a=b"));
    }

    @Test
    void reportsAMissingFile() {
        assertThat(new EnvFile(directory.resolve("missing.env")).exists()).isFalse();
    }
}
