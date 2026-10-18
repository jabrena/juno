package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.CompileException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A goal that talks to {@code arduino-cli}. Subclasses implement {@link #run()}; a compile error or an Arduino CLI
 * failure becomes a {@link MojoFailureException} here, once, for every goal.
 */
abstract class AbstractArduinoMojo extends AbstractMojo {
    /** Arduino CLI executable name or path. */
    @Parameter(property = "juno.arduinoCli", defaultValue = "arduino-cli", required = true)
    private String arduinoCli;

    /** Optional FQBN override. Compile goals derive it from {@code @Board}; monitor requires {@code juno.board}. */
    @Parameter(property = "juno.fqbn")
    private @Nullable String fqbn;

    /** Upload/monitor port. If omitted, the plugin discovers the only connected matching board. */
    @Parameter(property = "juno.port")
    private @Nullable String port;

    /** Runs {@code arduino-cli} itself, except in tests, which substitute a fake so no real process is touched. */
    private CommandExecutor commandExecutor = new SystemCommandExecutor();

    @Override
    public final void execute() throws MojoFailureException {
        try {
            run();
        } catch (CompileException | ArduinoCliException exception) {
            throw new MojoFailureException(exception.getMessage(), exception);
        }
    }

    /** The goal's work; may throw {@link CompileException} or {@link ArduinoCliException}. */
    abstract void run();

    final ArduinoCli arduinoCli() {
        return new ArduinoCli(arduinoCli, commandExecutor, message -> getLog().info(message));
    }

    /** Test seam: substitutes the executor {@link #arduinoCli()} builds its {@link ArduinoCli} with. */
    void useExecutor(CommandExecutor executor) {
        this.commandExecutor = executor;
    }

    /** The configured FQBN override, else {@code detectedFqbn}. */
    final String targetFqbn(String detectedFqbn) {
        return configured(fqbn).orElse(detectedFqbn);
    }

    final String resolvePort(ArduinoCli cli, String fqbn) {
        String resolvedPort = cli.resolvePort(fqbn, configured(port));
        getLog().info("Using Arduino board on " + resolvedPort);
        return resolvedPort;
    }

    /** A Maven parameter's trimmed value, or empty when it is unset or blank. */
    final Optional<String> configured(@Nullable String value) {
        return Optional.ofNullable(value).map(String::trim).filter(text -> !text.isEmpty());
    }
}
