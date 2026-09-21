package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code arduino-cli monitor} (confirmed on 1.5.1) can exit immediately with status 0 and no
 * diagnostic output in some environments — no real interactive terminal reaches the child process,
 * even with {@link ProcessBuilder#inheritIO()} — which would otherwise read as a normal, successful
 * build even though monitoring never actually happened. These tests use plain {@code sh -c} in place
 * of {@code arduino-cli} so they don't depend on the toolchain being installed.
 */
class SystemCommandExecutorTest {
    private final SystemCommandExecutor executor = new SystemCommandExecutor();

    @Test
    void nonInteractiveCommandCapturesOutputAndExitCode() {
        CommandResult result = executor.execute(List.of("sh", "-c", "echo hello; exit 3"), false);

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.output()).contains("hello");
    }

    @Test
    void interactiveCommandThatExitsImplausiblyFastFailsWithADiagnosticMessage() {
        assertThatThrownBy(() -> executor.execute(List.of("sh", "-c", "exit 0"), true))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("exited after just")
                .hasMessageContaining("no real interactive terminal");
    }

    @Test
    void interactiveCommandThatRunsLongEnoughSucceeds() {
        CommandResult result = executor.execute(List.of("sh", "-c", "sleep 3.2; exit 0"), true);

        assertThat(result.exitCode()).isEqualTo(0);
    }
}
