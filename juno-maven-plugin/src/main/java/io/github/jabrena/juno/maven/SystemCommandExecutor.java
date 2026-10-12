package io.github.jabrena.juno.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class SystemCommandExecutor implements CommandExecutor {
    /**
     * An interactive command (currently only {@code arduino-cli monitor}) is meant to run until the
     * user interrupts it; exiting well under this threshold right after starting is never legitimate
     * real usage. In some environments — no real interactive terminal reaching the child process,
     * even though {@link ProcessBuilder#inheritIO()} was used — {@code arduino-cli monitor} does
     * exactly that: exits with status 0 and no diagnostic output at all, silently doing nothing
     * instead of opening the monitor. Left unchecked, that reads as a normal, successful build.
     */
    private static final Duration MIN_INTERACTIVE_RUN = Duration.ofSeconds(3);

    @Override
    public CommandResult execute(Command command) {
        ProcessBuilder builder = new ProcessBuilder(command.arguments());
        if (command.isInteractive()) {
            builder.inheritIO();
        } else {
            builder.redirectErrorStream(true);
        }
        try {
            long started = System.nanoTime();
            Process process = builder.start();
            String output = command.isInteractive()
                    ? ""
                    : new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            if (command.isInteractive() && elapsed.compareTo(MIN_INTERACTIVE_RUN) < 0) {
                throw new ArduinoCliException("""
                        '%s' exited after just %d ms (exit code %d) instead of staying open until interrupted. \
                        This usually means the current environment has no real interactive terminal for Arduino \
                        CLI to attach to (e.g. launched from a non-interactive process or session). Try running \
                        that exact command directly from a real terminal instead of through this Maven goal."""
                        .formatted(command.display(), elapsed.toMillis(), exitCode));
            }
            return new CommandResult(exitCode, output);
        } catch (IOException exception) {
            throw new ArduinoCliException("Cannot run '" + command.program()
                    + "'. Install Arduino CLI or configure -Djuno.arduinoCli=/path/to/arduino-cli.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while running " + command.display(), exception);
        }
    }
}
