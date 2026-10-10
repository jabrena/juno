package io.github.jabrena.juno;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigTest {
    @Test
    void theDefaultReproducesTheRuntimeLimitsConstants() {
        assertThat(RuntimeConfig.DEFAULT.arenaBytes()).isEqualTo(RuntimeLimits.ARENA_CAPACITY_BYTES);
        assertThat(RuntimeConfig.DEFAULT.maxThreads()).isEqualTo(RuntimeLimits.MAX_THREADS);
        assertThat(RuntimeConfig.DEFAULT.threadStackBytes()).isEmpty();
    }

    @Test
    void anUnsetTaskStackFallsBackToTheBoardsOwnSize() {
        assertThat(RuntimeConfig.DEFAULT.threadStackBytes(4096)).isEqualTo(4096);
        assertThat(new RuntimeConfig(8192, 4, OptionalInt.of(1024)).threadStackBytes(4096)).isEqualTo(1024);
    }

    @Test
    void rejectsAnArenaThatIsTooSmallOrMisaligned() {
        assertThatThrownBy(() -> new RuntimeConfig(512, 4, OptionalInt.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining("arena");
        assertThatThrownBy(() -> new RuntimeConfig(8190, 4, OptionalInt.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining("multiple of 8");
    }

    @Test
    void rejectsTooFewSlotsAndAnInvalidStack() {
        assertThatThrownBy(() -> new RuntimeConfig(8192, 1, OptionalInt.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining("scheduler slots");
        assertThatThrownBy(() -> new RuntimeConfig(8192, 4, OptionalInt.of(100)))
                .isInstanceOf(CompileException.class).hasMessageContaining("task stack");
        assertThatThrownBy(() -> new RuntimeConfig(8192, 4, OptionalInt.of(1001)))
                .isInstanceOf(CompileException.class).hasMessageContaining("multiple of 8");
    }

    @Test
    void readsSizesInTheJvmsXmxAndXssSyntax() {
        RuntimeConfig config = RuntimeConfig.of(Optional.of("48k"), Optional.of("8192"));

        assertThat(config.arenaBytes()).isEqualTo(48 * 1024);
        assertThat(config.threadStackBytes()).hasValue(8192);
        assertThat(config.maxThreads()).isEqualTo(RuntimeLimits.MAX_THREADS);
        assertThat(RuntimeConfig.of(Optional.of("1M"), Optional.empty()).arenaBytes()).isEqualTo(1024 * 1024);
    }

    @Test
    void anAbsentOrBlankSizeKeepsTheDefault() {
        assertThat(RuntimeConfig.of(Optional.empty(), Optional.of(" "))).isEqualTo(RuntimeConfig.DEFAULT);
    }

    @Test
    void rejectsASizeThatIsNotANumberOfBytes() {
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.of("lots"), Optional.empty()))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.Xmx");
        assertThatThrownBy(() -> RuntimeConfig.of(Optional.empty(), Optional.of("4g")))
                .isInstanceOf(CompileException.class).hasMessageContaining("juno.Xss");
    }
}
