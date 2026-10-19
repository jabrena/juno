package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Powered Up hub version word")
class PoweredUpHubRemoteTest {

    @Test
    @DisplayName("Unpacks major, minor, BCD bug-fix and build from a version word")
    void shouldUnpackMajorMinorBugfixAndBuildFromAVersionWord() {
        // Given
        int version = 0x1234_5678;

        // When
        int[] parts = {
                PoweredUpHubRemote.versionMajor(version),
                PoweredUpHubRemote.versionMinor(version),
                PoweredUpHubRemote.versionBugfix(version),
                PoweredUpHubRemote.versionBuild(version)};

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
                PoweredUpHubRemote.versionMajor(0),
                PoweredUpHubRemote.versionMinor(0),
                PoweredUpHubRemote.versionBugfix(0),
                PoweredUpHubRemote.versionBuild(0)};

        // Then
        assertThat(parts).containsOnly(0);
    }
}
