package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PoweredUpHubRemoteTest {

    @Test
    void unpacksAFirmwareOrHardwareVersionWord() {
        int version = 0x1234_5678;

        assertThat(PoweredUpHubRemote.versionMajor(version)).isEqualTo(1);
        assertThat(PoweredUpHubRemote.versionMinor(version)).isEqualTo(2);
        assertThat(PoweredUpHubRemote.versionBugfix(version)).as("bug-fix 0x34 is BCD 34").isEqualTo(34);
        assertThat(PoweredUpHubRemote.versionBuild(version)).isEqualTo(0x5678);
    }

    @Test
    void aZeroVersionHasNoParts() {
        assertThat(PoweredUpHubRemote.versionMajor(0)).isZero();
        assertThat(PoweredUpHubRemote.versionMinor(0)).isZero();
        assertThat(PoweredUpHubRemote.versionBugfix(0)).isZero();
        assertThat(PoweredUpHubRemote.versionBuild(0)).isZero();
    }
}
