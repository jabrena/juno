package io.github.jabrena.juno.remote.lego.poweredup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Powered Up hub version word")
class PoweredUpHubRemoteTest {
    private final PoweredUpHubRemote hub = new PoweredUpHubRemote();

    @Test
    @DisplayName("Unpacks major, minor, BCD bug-fix and build from a version word")
    void shouldUnpackMajorMinorBugfixAndBuildFromAVersionWord() {
        // Given
        int version = 0x1234_5678;

        // When
        int[] parts = {
                hub.versionMajor(version),
                hub.versionMinor(version),
                hub.versionBugfix(version),
                hub.versionBuild(version)};

        // Then
        assertThat(parts)
                .as("major, minor, BCD bug-fix 0x34 read as 34, build")
                .containsExactly(1, 2, 34, 0x5678);
    }

    @Test
    @DisplayName("A zero version has no parts")
    void shouldHaveNoPartsWhenTheVersionIsZero() {
        // When
        int[] parts = {
                hub.versionMajor(0),
                hub.versionMinor(0),
                hub.versionBugfix(0),
                hub.versionBuild(0)};

        // Then
        assertThat(parts).containsOnly(0);
    }
}
