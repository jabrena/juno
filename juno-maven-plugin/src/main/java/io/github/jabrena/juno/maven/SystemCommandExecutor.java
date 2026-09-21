package io.github.jabrena.juno.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class SystemCommandExecutor implements CommandExecutor {
    /**
     * An interactive command (currently only {@code arduino-cli monitor}) is meant to run until the
     * user interrupts it; exiting well under this threshold right after starting is never legitimate
     * real usage. In some environments — no real interactive terminal reaching the child process,
     * even though {@link ProcessBuilder#inheritIO()} was used — {@code arduino-cli monitor} does
     * exactly that: exits with status 0 and no diagnostic output at all, silently doing nothing
     * instead of opening the monitor. Left unchecked, that reads as a normal, successful build.
     */
    private static final long MIN_INTERACTIVE_MILLIS = 3000;

    @Override
    public CommandResult execute(List<String> command, boolean interactive) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (interactive) {
            builder.inheritIO();
        } else {
            builder.redirectErrorStream(true);
        }

        try {
            long startNanos = System.nanoTime();
            Process process = builder.start();
            String output = interactive
                    ? ""
                    : new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            if (interactive && elapsedMillis < MIN_INTERACTIVE_MILLIS) {
                throw new ArduinoCliException("'" + String.join(" ", command) + "' exited after just "
                        + elapsedMillis + " ms (exit code " + exitCode + ") instead of staying open "
                        + "until interrupted. This usually means the current environment has no real "
                        + "interactive terminal for Arduino CLI to attach to (e.g. launched from a "
                        + "non-interactive process or session). Try running that exact command "
                        + "directly from a real terminal instead of through this Maven goal.");
            }
            return new CommandResult(exitCode, output);
        } catch (IOException exception) {
            throw new ArduinoCliException("Cannot run '" + command.getFirst()
                    + "'. Install Arduino CLI or configure -Djuno.arduinoCli=/path/to/arduino-cli.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while running " + String.join(" ", command), exception);
        }
    }
}
