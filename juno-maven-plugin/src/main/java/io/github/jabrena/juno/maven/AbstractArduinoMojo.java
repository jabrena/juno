package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.board.Board;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

abstract class AbstractArduinoMojo extends AbstractMojo {
    /** Arduino CLI executable name or path. */
    @Parameter(property = "juno.arduinoCli", defaultValue = "arduino-cli", required = true)
    private String arduinoCli;

    /** Optional FQBN override. Compile goals derive it from {@code @Board}; monitor uses Juno's default board. */
    @Parameter(property = "juno.fqbn")
    private String fqbn;

    /** Upload/monitor port. If omitted, the plugin discovers the only connected matching board. */
    @Parameter(property = "juno.port")
    private String port;

    final ArduinoCli arduinoCli() {
        return new ArduinoCli(arduinoCli, message -> getLog().info(message));
    }

    final String targetFqbn(String detectedFqbn) {
        return fqbn == null || fqbn.isBlank()
                ? detectedFqbn
                : fqbn.trim();
    }

    final String defaultFqbn() {
        return targetFqbn(Board.DEFAULT.fqbn());
    }

    final String resolvePort(ArduinoCli cli, String fqbn) {
        String resolvedPort = cli.resolvePort(fqbn, port);
        getLog().info("Using Arduino board on " + resolvedPort);
        return resolvedPort;
    }

    final MojoFailureException failure(RuntimeException exception) {
        return new MojoFailureException(exception.getMessage(), exception);
    }
}
