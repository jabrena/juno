package io.github.jabrena.juno.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Characterization of each board's memory profile")
class BoardProfileValuesTest {

    /** Pinned values, so a change to any board's budget is a deliberate, reviewed edit. */
    static Stream<Arguments> pinnedProfiles() {
        return Stream.of(
                arguments(Board.UNO_R3, 2048, 1024, 1024, 512, 512, MemoryAuthority.LINKER, 1),
                arguments(Board.UNO_R3_WIFI_REV2, 6144, 1024, 3072, 512, 1024, MemoryAuthority.LINKER, 1),
                arguments(Board.UNO_R4_WIFI, 32768, 8192, 25736, 2048, 8192, MemoryAuthority.LINKER, 1),
                arguments(Board.UNO_Q, 262144, 8192, 226928, 4096, 16384, MemoryAuthority.LOADER_POOL, 1),
                arguments(Board.NANO_ESP32, 327680, 8192, 296840, 8192, 32768, MemoryAuthority.RUNTIME_HEAP, 2),
                arguments(Board.AI_THINKER_ESP32_CAM, 327680, 8192, 305216, 8192, 32768, MemoryAuthority.RUNTIME_HEAP,
                        2),
                arguments(Board.M5_CARDPUTER, 327680, 8192, 273408, 8192, 32768, MemoryAuthority.RUNTIME_HEAP, 2),
                arguments(Board.PICO_2_W, 524288, 8192, 436176, 4096, 16384, MemoryAuthority.RUNTIME_HEAP, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pinnedProfiles")
    @DisplayName("Each board keeps its pinned arena, stack, carrier and authority values")
    void shouldKeepThePinnedValues(Board board, int sram, int defaultArena, int maxArena, int defaultStack,
                                   int maxStack, MemoryAuthority authority, int carriers) {
        BoardMemoryProfile profile = board.memoryProfile();

        assertThat(profile.internalSramBytes()).isEqualTo(sram);
        assertThat(profile.defaultArenaBytes()).isEqualTo(defaultArena);
        assertThat(profile.maxArenaBytes()).isEqualTo(maxArena);
        assertThat(profile.defaultStackBytes()).isEqualTo(defaultStack);
        assertThat(profile.maxStackBytes()).isEqualTo(maxStack);
        assertThat(profile.authority()).isEqualTo(authority);
        assertThat(board.core().carrierCount()).isEqualTo(carriers);
    }

    @DisplayName("Only the Nano ESP32 and AI Thinker ESP32-CAM record PSRAM")
    @org.junit.jupiter.api.Test
    void shouldRecordPsramOnlyWhereKnown() {
        assertThat(Board.NANO_ESP32.memoryProfile().psramBytes()).isEqualTo(OptionalInt.of(8 * 1024 * 1024));
        assertThat(Board.AI_THINKER_ESP32_CAM.memoryProfile().psramBytes()).isEqualTo(OptionalInt.of(4 * 1024 * 1024));
        assertThat(Board.M5_CARDPUTER.memoryProfile().psramBytes()).isEmpty();
        assertThat(Board.UNO_R3.memoryProfile().psramBytes()).isEmpty();
        assertThat(Board.UNO_R3_WIFI_REV2.memoryProfile().psramBytes()).isEmpty();
        assertThat(Board.UNO_R4_WIFI.memoryProfile().psramBytes()).isEmpty();
        assertThat(Board.UNO_Q.memoryProfile().psramBytes()).isEmpty();
        assertThat(Board.PICO_2_W.memoryProfile().psramBytes()).isEmpty();
    }
}
