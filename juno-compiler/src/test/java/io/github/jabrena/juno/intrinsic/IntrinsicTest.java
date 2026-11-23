package io.github.jabrena.juno.intrinsic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Which intrinsics may run on a core without the board's libraries")
class IntrinsicTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"MONITOR_ENTER", "REENTRANT_LOCK_LOCK", "ATOMIC_INT_INCREMENT_AND_GET", "THREAD_JOIN",
            "THREAD_SLEEP", "TASK_SCOPE_FORK_CALLABLE", "SCOPED_VALUE_GET", "THROW_RAISE", "LATCH_AWAIT",
            "STRING_CONCAT_APPEND_INT", "STRING_BUILDER_TO_STRING", "DURATION_TO_MILLIS"})
    @DisplayName("Runtime-only operations run on any core")
    void shouldRunRuntimeOperationsOnAnyCore(Intrinsic intrinsic) {
        assertThat(intrinsic.runsOnAnyCore()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(names = {"GPIO_DIGITAL_WRITE", "CLOCK_MILLIS", "DELAY_MICROS", "WIFI_BEGIN", "SD_OPEN",
            "STRING_VALUE_OF_DOUBLE", "STRING_CONCAT_APPEND_DOUBLE", "STRING_CONCAT_MEASURE_FLOAT", "UUID_RANDOM",
            "BIG_DECIMAL_ADD", "PROPERTIES_LOAD"})
    @DisplayName("Board libraries and floating-point formatting run on the core that has them")
    void shouldKeepBoardCallsOnTheirCore(Intrinsic intrinsic) {
        assertThat(intrinsic.runsOnAnyCore()).isFalse();
    }
}
