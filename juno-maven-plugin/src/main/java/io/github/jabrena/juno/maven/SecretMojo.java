package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.provisioner.SecretProvisioner;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jspecify.annotations.Nullable;

import java.io.Console;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores a named secret in the board's secure element, reports whether named secrets are stored, or empties the chip.
 * The store cannot enumerate what it holds, so every name is the user's: no name is built in. The program is
 * uploaded first: the plugin's own {@link SecretProvisioner}, unless {@code -Djuno.main} names another program that
 * speaks the same protocol. The value is typed at a hidden prompt and sent over USB serial as hex, so it never appears
 * on the command line, in the Maven log or on the SD card.
 *
 * <pre>
 * ./mvnw juno:secret -Djuno.board=arduino-uno-r4-wifi -Djuno.port=/dev/cu.usbmodemXXXX -Djuno.secret.key=wifi.ssid
 * ./mvnw juno:secret -Djuno.board=arduino-uno-r4-wifi -Djuno.port=/dev/cu.usbmodemXXXX \
 *   -Djuno.secret.list=wifi.ssid,wifi.password
 * </pre>
 */
@Mojo(name = "secret", requiresDependencyResolution = ResolutionScope.COMPILE)
public final class SecretMojo extends AbstractJunoMojo {
    private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REPLY_TIMEOUT = Duration.ofSeconds(5);
    private static final String CONFIRMATION = "CLEAR";

    /** The name to store; the board keeps it next to its value, so no slot number is chosen. */
    @Parameter(property = "juno.secret.key")
    private @Nullable String key;

    /** Comma-separated names to check: prints whether each one is stored, without its value. */
    @Parameter(property = "juno.secret.list")
    private @Nullable String list;

    /** Empties every data slot on the chip after asking for the word {@code CLEAR}. */
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
    Optional<BundledProgram> defaultProgram() {
        return Optional.of(new BundledProgram(SecretProvisioner.class));
    }

    @Override
    void run() {
        String name = configured(key).orElse(null);
        List<String> listed = listedNames();
        int modes = (name == null ? 0 : 1) + (listed.isEmpty() ? 0 : 1) + (clear ? 1 : 0);
        if (modes != 1) {
            throw new ArduinoCliException("Choose exactly one of -Djuno.secret.key=<name>, "
                    + "-Djuno.secret.list=<name>[,<name>...] or -Djuno.secret.clear=true");
        }
        if (name != null) {
            SecretProtocol.validateName(name);
        }
        listed.forEach(SecretProtocol::validateName);
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
        upload(cli, sketch, port);

        try (SerialSession session = sessions.open(port, executor())) {
            awaitReady(session);
            if (name != null && value != null) {
                store(session, name, value);
            } else if (clear) {
                clearAll(session);
            } else {
                printStates(session, listed);
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
        String answer = terminal.readLine("Type " + CONFIRMATION + " to empty every data slot on the chip: ");
        return CONFIRMATION.equals(answer);
    }

    private void awaitReady(SerialSession session) {
        String line = session.nextLine(BOOT_TIMEOUT);
        while (!"READY".equals(line)) {
            line = session.nextLine(BOOT_TIMEOUT);
        }
    }

    /**
     * The board's next answer. The board repeats {@code READY} until its first request arrives, so one may still be on
     * its way after the request was sent; those lines are skipped.
     */
    private static String nextReply(SerialSession session) {
        String line = session.nextLine(REPLY_TIMEOUT);
        while ("READY".equals(line)) {
            line = session.nextLine(REPLY_TIMEOUT);
        }
        return line;
    }

    private void store(SerialSession session, String name, byte[] value) {
        session.send(SecretProtocol.setRequest(name, value));
        Arrays.fill(value, (byte) 0);
        String reply = nextReply(session);
        if (!reply.equals("OK " + name)) {
            throw new ArduinoCliException("The board did not store " + name + ": " + reply);
        }
        getLog().info("Stored " + name + " in the chip");
    }

    /**
     * The names {@code juno.secret.list} gives. A boolean there is the old switch, which listed names built into the
     * program; there are none now, so it fails with how to name them.
     */
    private List<String> listedNames() {
        String names = configured(list).orElse("");
        if (names.equalsIgnoreCase("true") || names.equalsIgnoreCase("false")) {
            throw new ArduinoCliException("-Djuno.secret.list takes the names to check, not true or false: "
                    + "for example -Djuno.secret.list=wifi.ssid,wifi.password");
        }
        return Arrays.stream(names.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private void printStates(SerialSession session, List<String> names) {
        for (String listedName : names) {
            session.send(SecretProtocol.listRequest(listedName));
            String reply = nextReply(session);
            if (!reply.startsWith("STATE " + listedName + " ")) {
                throw new ArduinoCliException("The board did not report " + listedName + ": " + reply);
            }
            getLog().info(reply);
        }
    }

    private void clearAll(SerialSession session) {
        session.send(SecretProtocol.clearAllRequest());
        String reply = nextReply(session);
        if (!reply.equals("OK CLEAR ALL")) {
            throw new ArduinoCliException("The board did not empty every data slot: " + reply);
        }
        getLog().info("Emptied every data slot on the chip");
    }

    /** Test seam: substitutes the terminal and the serial opener. */
    void useTerminalAndSessions(Terminal replacementTerminal, SessionOpener replacementSessions) {
        this.terminal = Objects.requireNonNull(replacementTerminal);
        this.sessions = Objects.requireNonNull(replacementSessions);
    }
}
