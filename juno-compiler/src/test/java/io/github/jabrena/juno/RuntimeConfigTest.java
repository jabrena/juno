package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Board;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@NullMarked
@DisplayName("Runtime configuration limits")
class RuntimeConfigTest {

    static Stream<Arguments> invalidSchedules() {
        return Stream.of(
                arguments(8192, 1, OptionalInt.empty(), "scheduler slots"),
                arguments(8192, 4, OptionalInt.of(100), "task stack"),
                arguments(8192, 4, OptionalInt.of(1001), "multiple of 8"));
    }

    @Test
    @DisplayName("The default arena matches the runtime limits constant")
    void shouldUseTheArenaOfTheRuntimeLimits() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.arenaBytes()).isEqualTo(RuntimeLimits.ARENA_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("The default thread count matches the runtime limits constant")
    void shouldUseTheMaxThreadsOfTheRuntimeLimits() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.maxThreads()).isEqualTo(RuntimeLimits.MAX_THREADS);
    }

    @Test
    @DisplayName("The default leaves the thread stack unset")
    void shouldLeaveTheThreadStackUnsetByDefault() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.threadStackBytes()).isEmpty();
    }

    @Test
    @DisplayName("An unset task stack falls back to the board's own size")
    void shouldFallBackToTheBoardsOwnStackSizeWhenUnset() {
        // Given
        RuntimeConfig configured = new RuntimeConfig(8192, 4, OptionalInt.of(1024));

        // When
        int[] stacks = {RuntimeConfig.DEFAULT.threadStackBytes(4096), configured.threadStackBytes(4096)};

        // Then
        assertThat(stacks).containsExactly(4096, 1024);
    }

    @ParameterizedTest(name = "rejects arena of {0} bytes: {2}")
    @CsvSource({"256, arena, arena", "8190, multiple of 8, multiple of 8"})
    @DisplayName("Rejects an arena that is too small or misaligned")
    void shouldRejectAnArenaThatIsTooSmallOrMisaligned(int bytes, String message, String description) {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(bytes, 4, OptionalInt.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining(message);
    }

    @Test
    @DisplayName("Accepts a half-kilobyte arena for memory-constrained programs")
    void shouldAcceptAHalfKilobyteArenaForMemoryConstrainedPrograms() {
        // When & Then
        assertThat(new RuntimeConfig(512, 4, OptionalInt.empty()).arenaBytes()).isEqualTo(512);
    }

    @ParameterizedTest(name = "rejects {3}")
    @MethodSource("invalidSchedules")
    @DisplayName("Rejects too few scheduler slots or an invalid task stack")
    void shouldRejectTooFewSlotsAndAnInvalidStack(int bytes, int slots, OptionalInt stack, String message) {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(bytes, slots, stack))
                .isInstanceOf(CompileException.class).hasMessageContaining(message);
    }

    @Test
    @DisplayName("Reads a kilobyte arena from the Xmx syntax")
    void shouldReadAKilobyteArenaFromTheXmxSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.of("48k"), Optional.empty()).arenaBytes()).isEqualTo(48 * 1024);
    }

    @Test
    @DisplayName("Reads a task stack from the Xss syntax")
    void shouldReadATaskStackFromTheXssSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of("8192")).threadStackBytes()).hasValue(8192);
    }

    @Test
    @DisplayName("Keeps the default thread count when only the arena and stack are set")
    void shouldKeepTheDefaultThreadCountWhenOnlyTheArenaAndStackAreSet() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.of("48k"), Optional.of("8192")).maxThreads())
                .isEqualTo(RuntimeLimits.MAX_THREADS);
    }

    @Test
    @DisplayName("Reads a megabyte arena from the Xmx syntax")
    void shouldReadAMegabyteArenaFromTheXmxSyntax() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.of("1M"), Optional.empty()).arenaBytes()).isEqualTo(1024 * 1024);
    }

    @Test
    @DisplayName("Reads an explicit scheduler slot limit")
    void shouldReadAnExplicitSchedulerSlotLimit() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("3")).maxThreads()).isEqualTo(3);
    }

    @Test
    @DisplayName("Rejects a scheduler slot limit that is not a number")
    void shouldRejectANonNumericSchedulerSlotLimit() {
        // When & Then
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("many")))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.maxThreads");
    }

    @Test
    @DisplayName("An absent or blank size keeps the default")
    void shouldKeepTheDefaultForAnAbsentOrBlankSize() {
        // When & Then
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of(" "))).isEqualTo(RuntimeConfig.DEFAULT);
    }

    @Test
    @DisplayName("Applies the Mega AVR core's arena limit")
    void shouldApplyTheMegaAvrCoresArenaLimit() {
        // When
        RuntimeConfig config = RuntimeConfig.DEFAULT.forBoard(Board.UNO_R3_WIFI_REV2);

        // Then
        assertThat(config.arenaBytes()).isEqualTo(RuntimeLimits.AVR_ARENA_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("Rejects an arena above the Mega AVR core's limit")
    void shouldRejectAnArenaAboveTheMegaAvrCoresLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(RuntimeLimits.AVR_MAX_ARENA_BYTES + 8, 4, OptionalInt.empty())
                        .forBoard(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("at most " + RuntimeLimits.AVR_MAX_ARENA_BYTES);
    }

    @Test
    @DisplayName("Rejects a task stack above the Mega AVR core's limit")
    void shouldRejectATaskStackAboveTheMegaAvrCoresLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(1024, 4,
                        OptionalInt.of(RuntimeLimits.AVR_MAX_THREAD_STACK_BYTES + 8)).forBoard(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("at most " + RuntimeLimits.AVR_MAX_THREAD_STACK_BYTES);
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
