package io.github.jabrena.juno.board;

import io.github.jabrena.juno.RuntimeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Board memory profiles")
class BoardMemoryProfileTest {

    @ParameterizedTest(name = "{0} has a consistent profile")
    @EnumSource(Board.class)
    @DisplayName("Every board's profile is ordered and aligned")
    void shouldHaveAnOrderedAndAlignedProfile(Board board) {
        BoardMemoryProfile profile = board.memoryProfile();

        assertThat(profile.defaultArenaBytes()).isBetween(RuntimeConfig.MIN_ARENA_BYTES, profile.maxArenaBytes());
        assertThat(profile.maxArenaBytes()).isLessThan(profile.internalSramBytes());
        assertThat(profile.defaultStackBytes()).isBetween(RuntimeConfig.MIN_STACK_BYTES, profile.maxStackBytes());
        assertThat(profile.reservedBytes()).isEqualTo(profile.internalSramBytes() - profile.maxArenaBytes());
    }

    @ParameterizedTest(name = "{0} keeps the arena in internal SRAM")
    @EnumSource(Board.class)
    @DisplayName("No profile reads its arena size from PSRAM")
    void shouldNotDeriveTheArenaFromPsram(Board board) {
        // The PSRAM figure is external metadata: the arena bounds never move with it.
        BoardMemoryProfile profile = board.memoryProfile();
        assertThat(profile.maxArenaBytes()).isLessThan(profile.internalSramBytes());
    }

    @Test
    @DisplayName("A profile with an arena above internal SRAM is rejected")
    void shouldRejectAnArenaAboveInternalSram() {
        assertThatThrownBy(() -> new BoardMemoryProfile(8192, 1024, 8192, 512, 512, MemoryAuthority.LINKER,
                OptionalInt.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A profile with a size that is not a multiple of 8 is rejected")
    void shouldRejectAMisalignedProfile() {
        assertThatThrownBy(() -> new BoardMemoryProfile(8192, 1020, 2048, 512, 512, MemoryAuthority.LINKER,
                OptionalInt.empty())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("multiple of 8");
    }
}
