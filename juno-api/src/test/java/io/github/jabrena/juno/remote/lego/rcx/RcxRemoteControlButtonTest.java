package io.github.jabrena.juno.remote.lego.rcx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RCX remote control buttons")
class RcxRemoteControlButtonTest {

    @ParameterizedTest
    @EnumSource(value = RcxRemoteControlButton.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Each button's mask maps back to that button")
    void shouldRoundTripEveryButtonsMask(RcxRemoteControlButton button) {
        // When & Then
        assertThat(RcxRemoteControlButton.fromMask(button.mask())).isSameAs(button);
    }

    @ParameterizedTest
    @EnumSource(RcxRemoteControlButton.class)
    @DisplayName("Each button's label is its constant name, without enum reflection on the board")
    void shouldLabelEveryButtonByItsName(RcxRemoteControlButton button) {
        // When & Then
        assertThat(button.label()).isEqualTo(button.name());
    }

    @ParameterizedTest
    @EnumSource(value = RcxRemoteControlButton.class, names = {"NONE", "UNKNOWN"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("A real button is pressed in a mask that has its bit, alone or with others, and not otherwise")
    void shouldFindARealButtonInAMask(RcxRemoteControlButton button) {
        // When & Then
        assertThat(button.isPressedIn(button.mask())).isTrue();
        assertThat(button.isPressedIn(0xFFFF)).isTrue();
        assertThat(button.isPressedIn(0xFFFF & ~button.mask())).isFalse();
    }

    @Test
    @DisplayName("NONE and UNKNOWN are never pressed, and a combination or an unused bit maps to UNKNOWN")
    void shouldTreatPlaceholdersAndCombinationsAsNoButton() {
        // When & Then
        assertThat(RcxRemoteControlButton.NONE.isPressedIn(0xFFFF)).isFalse();
        assertThat(RcxRemoteControlButton.UNKNOWN.isPressedIn(0xFFFF)).isFalse();
        assertThat(RcxRemoteControlButton.fromMask(RcxRemoteControlButton.A_UP.mask()
                | RcxRemoteControlButton.STOP.mask())).isSameAs(RcxRemoteControlButton.UNKNOWN);
        assertThat(RcxRemoteControlButton.fromMask(0x10000)).isSameAs(RcxRemoteControlButton.UNKNOWN);
    }
}
