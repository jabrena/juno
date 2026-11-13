package io.github.jabrena.juno;

import io.github.jabrena.juno.ResolvedRuntimeConfig.Source;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Runtime configuration limits")
class RuntimeConfigTest {

    static Stream<Arguments> invalidSchedules() {
        return Stream.of(
                arguments(8192, OptionalInt.of(100), "task stack"),
                arguments(8192, OptionalInt.of(1001), "multiple of 8"));
    }

    static Stream<Arguments> knownGoodArenas() {
        return Stream.of(
                arguments(Board.UNO_R4_WIFI, 11264),
                arguments(Board.UNO_Q, 92 * 1024),
                arguments(Board.M5_CARDPUTER, 12 * 1024),
                arguments(Board.NANO_ESP32, 24 * 1024));
    }

    @Test
    @DisplayName("The default request leaves every size unset")
    void shouldLeaveEverySizeUnsetByDefault() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.arenaBytes()).isEmpty();
        assertThat(RuntimeConfig.DEFAULT.threadStackBytes()).isEmpty();
        assertThat(RuntimeConfig.DEFAULT.carrierCountOverride()).isEmpty();
    }

    @Test
    @DisplayName("An unset arena and stack take the board's defaults, reported as board defaults")
    void shouldResolveUnsetSizesToTheBoardDefaults() {
        // When
        ResolvedRuntimeConfig resolved = RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R4_WIFI);

        // Then
        assertThat(resolved.arenaBytes()).isEqualTo(8192);
        assertThat(resolved.arenaSource()).isEqualTo(Source.BOARD_DEFAULT);
        assertThat(resolved.threadStackBytes()).isEqualTo(2048);
        assertThat(resolved.stackSource()).isEqualTo(Source.BOARD_DEFAULT);
        assertThat(resolved.carrierCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("An explicit value equal to the old default is an override on a board that accepts it")
    void shouldReportAnExplicitDefaultValueAsAnOverride() {
        // When
        ResolvedRuntimeConfig resolved = RuntimeConfig.of(Optional.of("8k"), Optional.empty())
                .resolveFor(Board.UNO_R4_WIFI);

        // Then
        assertThat(resolved.arenaBytes()).isEqualTo(8192);
        assertThat(resolved.arenaSource()).isEqualTo(Source.OVERRIDE);
    }

    @Test
    @DisplayName("An explicit 8 KiB arena is rejected on the UNO WiFi Rev2 instead of silently replaced")
    void shouldRejectAnExplicitEightKilobyteArenaOnTheRev2() {
        // When & Then
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.of("8k"), Optional.empty())
                .resolveFor(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO WiFi Rev2")
                .hasMessageContaining("8192-byte arena")
                .hasMessageContaining("at most 3072");
    }

    @Test
    @DisplayName("The UNO WiFi Rev2 takes its own 1 KiB default arena")
    void shouldApplyTheRev2DefaultArena() {
        // When
        ResolvedRuntimeConfig resolved = RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R3_WIFI_REV2);

        // Then
        assertThat(resolved.arenaBytes()).isEqualTo(RuntimeLimits.AVR_ARENA_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("An unset task stack falls back to the board's own size")
    void shouldFallBackToTheBoardsOwnStackSizeWhenUnset() {
        // When
        ResolvedRuntimeConfig unset = RuntimeConfig.DEFAULT.resolveFor(Board.UNO_Q);
        ResolvedRuntimeConfig explicit = new RuntimeConfig(8192, OptionalInt.of(1024)).resolveFor(Board.UNO_Q);

        // Then
        assertThat(unset.threadStackBytes()).isEqualTo(4096);
        assertThat(explicit.threadStackBytes()).isEqualTo(1024);
        assertThat(explicit.stackSource()).isEqualTo(Source.OVERRIDE);
    }

    @ParameterizedTest(name = "rejects arena of {0} bytes: {2}")
    @CsvSource({"256, arena, arena", "8190, multiple of 8, multiple of 8"})
    @DisplayName("Rejects an arena that is too small or misaligned")
    void shouldRejectAnArenaThatIsTooSmallOrMisaligned(int bytes, String message, String description) {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(bytes, OptionalInt.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining(message);
    }

    @Test
    @DisplayName("Accepts a half-kilobyte arena for memory-constrained programs")
    void shouldAcceptAHalfKilobyteArenaForMemoryConstrainedPrograms() {
        // When & Then
        assertThat(new RuntimeConfig(512, OptionalInt.empty()).resolveFor(Board.UNO_R3).arenaBytes()).isEqualTo(512);
    }

    @ParameterizedTest(name = "rejects {2}")
    @MethodSource("invalidSchedules")
    @DisplayName("Rejects an invalid carrier stack")
    void shouldRejectAnInvalidStack(int bytes, OptionalInt stack, String message) {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(bytes, stack))
                .isInstanceOf(CompileException.class).hasMessageContaining(message);
    }

    @Test
    @DisplayName("Reads a kilobyte arena from the Xmx syntax")
    void shouldReadAKilobyteArenaFromTheXmxSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.of("48k"), Optional.empty()).arenaBytes()).hasValue(48 * 1024);
    }

    @Test
    @DisplayName("Reads a task stack from the Xss syntax")
    void shouldReadATaskStackFromTheXssSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of("8192")).threadStackBytes()).hasValue(8192);
    }

    @Test
    @DisplayName("Reads the pipeline carrier override")
    void shouldReadTheCarrierOverride() {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        assertThat(config.resolveFor(Board.NANO_ESP32).carrierCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("A program without threads runs on one carrier even on a dual-core board")
    void shouldRunAThreadFreeProgramOnOneCarrier() {
        ResolvedRuntimeConfig resolved = RuntimeConfig.DEFAULT.resolveFor(Board.NANO_ESP32);

        assertThat(resolved.carrierCount(false)).isEqualTo(1);
        assertThat(resolved.carrierCount(true)).isEqualTo(2);
    }

    @Test
    @DisplayName("Rejects a carrier count outside the supported range")
    void shouldRejectAnInvalidCarrierCount() {
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("3")))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.carriers");
    }

    @Test
    @DisplayName("Rejects more carriers than the board core provides")
    void shouldRejectMoreCarriersThanTheBoardProvides() {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("2"));

        assertThatThrownBy(() -> config.resolveFor(Board.UNO_R4_WIFI))
                .isInstanceOf(CompileException.class).hasMessageContaining("exceeds");
    }

    @Test
    @DisplayName("Reads a megabyte arena from the Xmx syntax")
    void shouldReadAMegabyteArenaFromTheXmxSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.of("1M"), Optional.empty()).arenaBytes()).hasValue(1024 * 1024);
    }

    @Test
    @DisplayName("An absent or blank size keeps the default")
    void shouldKeepTheDefaultForAnAbsentOrBlankSize() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of(" "))).isEqualTo(RuntimeConfig.DEFAULT);
    }

    @Test
    @DisplayName("Rejects an arena above the UNO R3's maximum, naming the board")
    void shouldRejectAnArenaAboveTheR3Maximum() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(1032, OptionalInt.empty()).resolveFor(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3")
                .hasMessageContaining("at most 1024");
    }

    @Test
    @DisplayName("Rejects a task stack above the UNO WiFi Rev2's maximum")
    void shouldRejectATaskStackAboveTheRev2Maximum() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(1024, OptionalInt.of(1032)).resolveFor(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xss can be at most 1024");
    }

    @Test
    @DisplayName("Rejects an arena of one gigabyte on the UNO R4 WiFi, naming the board and its maximum")
    void shouldRejectAGigabyteArenaOnTheR4() {
        // When & Then
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.of("1g"), Optional.empty()).resolveFor(Board.UNO_R4_WIFI))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R4 WiFi")
                .hasMessageContaining("juno.Xmx can be at most 25736 bytes")
                .hasMessageContaining("32768 bytes internal SRAM")
                .hasMessageContaining("never uses PSRAM");
    }

    @ParameterizedTest(name = "accepts known-good arena on {0}")
    @MethodSource("knownGoodArenas")
    @DisplayName("Accepts every arena known to work on hardware")
    void shouldAcceptKnownGoodArenas(Board board, int bytes) {
        // When & Then
        assertThat(new RuntimeConfig(bytes, OptionalInt.empty()).resolveFor(board).arenaBytes()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("Accepts the 1536-byte and 2 KiB stacks used by UNO R4 programs")
    void shouldAcceptTheR4StacksInUse() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of("1536")).resolveFor(Board.UNO_R4_WIFI)
                .threadStackBytes()).isEqualTo(1536);
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of("2k")).resolveFor(Board.UNO_R4_WIFI)
                .threadStackBytes()).isEqualTo(2048);
    }

    @ParameterizedTest(name = "a stack one step above the maximum is rejected on {0}")
    @EnumSource(Board.class)
    @DisplayName("Rejects a task stack above the maximum on every board")
    void shouldRejectAStackAboveTheMaximumOnEveryBoard(Board board) {
        // Given
        int tooLarge = board.memoryProfile().maxStackBytes() + 8;

        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(OptionalInt.empty(), OptionalInt.of(tooLarge), OptionalInt.empty())
                .resolveFor(board))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining(board.displayName())
                .hasMessageContaining("juno.Xss can be at most " + board.memoryProfile().maxStackBytes());
    }

    @ParameterizedTest(name = "accepts a stack at the maximum on {0}")
    @EnumSource(Board.class)
    @DisplayName("Accepts a task stack at the maximum on every board")
    void shouldAcceptAStackAtTheMaximumOnEveryBoard(Board board) {
        // Given
        int atMax = board.memoryProfile().maxStackBytes();

        // When & Then
        assertThat(new RuntimeConfig(OptionalInt.empty(), OptionalInt.of(atMax), OptionalInt.empty())
                .resolveFor(board).threadStackBytes()).isEqualTo(atMax);
    }

    @Test
    @DisplayName("Rejects an Xmx size that is not a number of bytes")
    void shouldRejectAnXmxSizeThatIsNotANumberOfBytes() {
        // When & Then
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.of("lots"), Optional.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.Xmx");
    }

    @Test
    @DisplayName("Rejects an Xss size that is not a number of bytes")
    void shouldRejectAnXssSizeThatIsNotANumberOfBytes() {
        // When & Then
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.empty(), Optional.of("4g")))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.Xss");
    }
}
