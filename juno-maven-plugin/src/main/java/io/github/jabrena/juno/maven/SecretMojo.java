package io.github.jabrena.juno.maven;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jspecify.annotations.Nullable;

import java.io.Console;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

/**
 * Stores a named secret in the board's ATECC608 and lists or clears the stored names. The program is uploaded first, which
 * is {@code SecretProvisioner} in {@code juno-examples}: pass it with {@code -Djuno.main}. The value is typed at a hidden
 * prompt and sent over USB serial as hex, so it never appears on the command line, in the Maven log or on the SD card.
 *
 * <pre>
 * ./mvnw -f juno-examples/pom.xml juno:secret -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
 *   -Djuno.board=arduino-uno-r4-wifi -Djuno.port=/dev/cu.usbmodemXXXX -Djuno.secret.key=wifi.ssid
 * </pre>
 */
@Mojo(name = "secret", requiresDependencyResolution = ResolutionScope.COMPILE)
public final class SecretMojo extends AbstractJunoMojo {
    private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REPLY_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONFIRMATION = "CLEAR";

    /** The name to store, one of the names in {@link SecretProtocol#KNOWN_NAMES}. */
    @Parameter(property = "juno.secret.key")
    private @Nullable String key;

    /** Prints which names are stored, without their values. */
    @Parameter(property = "juno.secret.list", defaultValue = "false")
    private boolean list;

    /** Empties every name after asking for the word {@code CLEAR}, then prints the list. */
    @Parameter(property = "juno.secret.clear", defaultValue = "false")
    private boolean clear;

    /** The terminal the goal prompts on; a test substitutes its own. */
    private Terminal terminal = Terminal.system();

    /** Opens the serial conversation with the board; a test substitutes an in-memory one. */
    private SessionOpener sessions = (port, executor) -> SerialSession.open(Path.of(port), executor, BOOT_TIMEOUT);

    /** Terminal input for the goal. */
    interface Terminal {
        boolean present();

        /** A hidden prompt's answer; an empty array when none was given. */
        char[] readSecret(String prompt);

        /** A visible prompt's answer; an empty string when none was given. */
        String readLine(String prompt);

        static Terminal system() {
            Console console = System.console();
            if (console == null) {
                return new Terminal() {
                    @Override
                    public boolean present() {
                        return false;
                    }

                    @Override
                    public char[] readSecret(String prompt) {
                        return new char[0];
                    }

                    @Override
                    public String readLine(String prompt) {
                        return "";
                    }
                };
            }
            return new Terminal() {
                @Override
                public boolean present() {
                    return true;
                }

                @Override
                public char[] readSecret(String prompt) {
                    char[] typed = console.readPassword("%s", prompt);
                    return typed == null ? new char[0] : typed;
                }

                @Override
                public String readLine(String prompt) {
                    String line = console.readLine("%s", prompt);
                    return line == null ? "" : line;
                }
            };
        }
    }

    /** Opens a serial conversation with {@code port}, configured through {@code executor}. */
    interface SessionOpener {
        SerialSession open(String port, CommandExecutor executor);
    }

    @Override
    void run() {
        String name = configured(key).orElse(null);
        int modes = (name == null ? 0 : 1) + (list ? 1 : 0) + (clear ? 1 : 0);
        if (modes != 1) {
            throw new ArduinoCliException("Choose exactly one of -Djuno.secret.key=<name>, -Djuno.secret.list=true or "
                    + "-Djuno.secret.clear=true");
        }
        if (name != null && !SecretProtocol.isKnown(name)) {
            throw new ArduinoCliException("Unknown secret name " + name + "; the supported names are "
                    + SecretProtocol.KNOWN_NAMES + ". Nothing was uploaded or written.");
        }
        if (!terminal.present() && (name != null || clear)) {
            throw new ArduinoCliException("juno:secret reads values and confirmations from a terminal, which this session "
                    + "does not have. Nothing was uploaded or written.");
        }
        byte[] value = null;
        if (name != null) {
            value = readValue(name);
        }
        if (clear && !confirmed()) {
            throw new ArduinoCliException("CLEAR was not typed. Nothing was uploaded or written.");
        }

        CompiledSketch sketch = compileSketch();
        ArduinoCli cli = arduinoCli();
        verifySketch(cli, sketch);
        String port = resolvePort(cli, sketch.fqbn());
        getLog().info("Uploading the secret provisioning program");
        cli.upload(sketch.fqbn(), port, sketch.directory());

        try (SerialSession session = sessions.open(port, executor())) {
            awaitReady(session);
            if (name != null && value != null) {
                store(session, name, value);
            } else if (clear) {
                printList(session);
                clearAll(session);
            } else {
                printList(session);
            }
        }
    }

    /** Reads the value at a hidden prompt and checks it for the board before anything is uploaded. */
    private byte[] readValue(String name) {
        char[] typed = terminal.readSecret("Value for " + name + " (hidden): ");
        if (typed.length == 0) {
            throw new ArduinoCliException("No value was typed for " + name + ". Nothing was uploaded or written.");
        }
        try {
            return SecretProtocol.valueBytes(name, typed);
        } finally {
            Arrays.fill(typed, '\0');
        }
    }

    private boolean confirmed() {
        String answer = terminal.readLine("Type " + CONFIRMATION + " to clear every stored name: ");
        return CONFIRMATION.equals(answer);
    }

    private void awaitReady(SerialSession session) {
        String line = session.nextLine(BOOT_TIMEOUT);
        while (!"READY".equals(line)) {
            line = session.nextLine(BOOT_TIMEOUT);
        }
    }

    private void store(SerialSession session, String name, byte[] value) {
        session.send(SecretProtocol.setRequest(name, value));
        Arrays.fill(value, (byte) 0);
        String reply = session.nextLine(REPLY_TIMEOUT);
        if (!reply.equals("OK " + name)) {
            throw new ArduinoCliException("The board did not store " + name + ": " + reply);
        }
        getLog().info("Stored " + name + " in the chip");
    }

    private void printList(SerialSession session) {
        session.send(SecretProtocol.listRequest());
        String line = session.nextLine(REPLY_TIMEOUT);
        while (!"END".equals(line)) {
            getLog().info(line);
            line = session.nextLine(REPLY_TIMEOUT);
        }
    }

    private void clearAll(SerialSession session) {
        for (String name : SecretProtocol.KNOWN_NAMES) {
            session.send(SecretProtocol.clearRequest(name));
            String reply = session.nextLine(REPLY_TIMEOUT);
            if (!reply.equals("OK " + name)) {
                throw new ArduinoCliException("The board did not clear " + name + ": " + reply);
            }
            getLog().info("Cleared " + name);
        }
    }

    /** Test seam: substitutes the terminal and the serial opener. */
    void useTerminalAndSessions(Terminal replacementTerminal, SessionOpener replacementSessions) {
        this.terminal = Objects.requireNonNull(replacementTerminal);
        this.sessions = Objects.requireNonNull(replacementSessions);
    }
}
