package io.github.jabrena.juno.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class SystemCommandExecutor implements CommandExecutor {
    @Override
    public CommandResult execute(List<String> command, boolean interactive) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (interactive) {
            builder.inheritIO();
        } else {
            builder.redirectErrorStream(true);
        }

        try {
            Process process = builder.start();
            String output = interactive
                    ? ""
                    : new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.waitFor(), output);
        } catch (IOException exception) {
            throw new ArduinoCliException("Cannot run '" + command.getFirst()
                    + "'. Install Arduino CLI or configure -Djuno.arduinoCli=/path/to/arduino-cli.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ArduinoCliException("Interrupted while running " + String.join(" ", command), exception);
        }
    }
}
