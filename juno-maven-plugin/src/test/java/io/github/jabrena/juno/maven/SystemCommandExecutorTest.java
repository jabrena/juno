package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
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
@DisplayName("System command execution")
class SystemCommandExecutorTest {
    private final SystemCommandExecutor executor = new SystemCommandExecutor();

    @Test
    @DisplayName("Captures the output and exit code of a non-interactive command")
    void shouldCaptureOutputAndExitCodeOfANonInteractiveCommand() {
        // Given
        Command command = new Command(List.of("sh", "-c", "echo hello; exit 3"), Command.Mode.CAPTURED);

        // When
        CommandResult result = executor.execute(command);

        // Then
        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.output()).contains("hello");
    }

    @Test
    @DisplayName("Fails an interactive command that exits implausibly fast with a diagnostic message")
    void shouldFailAnInteractiveCommandThatExitsImplausiblyFastWithADiagnosticMessage() {
        // Given
        Command command = new Command(List.of("sh", "-c", "exit 0"), Command.Mode.INTERACTIVE);

        // When & Then
        assertThatThrownBy(() -> executor.execute(command))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("exited after just")
                .hasMessageContaining("no real interactive terminal");
    }

    @Test
    @DisplayName("Succeeds for an interactive command that runs long enough")
    void shouldSucceedForAnInteractiveCommandThatRunsLongEnough() {
        // Given
        Command command = new Command(List.of("sh", "-c", "sleep 3.2; exit 0"), Command.Mode.INTERACTIVE);

        // When
        CommandResult result = executor.execute(command);

        // Then
        assertThat(result.exitCode()).isEqualTo(0);
    }
}
