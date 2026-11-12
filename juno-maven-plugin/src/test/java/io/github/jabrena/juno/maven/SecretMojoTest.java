package io.github.jabrena.juno.maven;

import io.github.jabrena.juno.maven.fixture.TinyProgramClasspath;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static io.github.jabrena.juno.maven.MojoConfig.configuration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code juno:secret} input checks. Every check here fails before the program is compiled or uploaded, so these tests
 * run the goal with a fake executor and assert that it ran no command. The test JVM has no terminal, so the goal's
 * console check is the one that answers when a key is given.
 */
@DisplayName("juno:secret goal input checks")
class SecretMojoTest extends AbstractMojoTestCase {
    @BeforeEach
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    @DisplayName("Fails before any command when no mode is given")
    void shouldRequireExactlyOneMode() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, null, false, false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Choose exactly one");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Fails before any command when a key and the list mode are both given")
    void shouldRejectTwoModes() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.ssid", true, false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Choose exactly one");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Fails before any command when the name is not one of the supported names")
    void shouldRejectUnknownName() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.unknown", false, false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Unknown secret name wifi.unknown");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Fails before any command when a value is requested without a terminal")
    void shouldRequireATerminalForValues() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.ssid", false, false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("from a terminal");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Fails before any command when clear is requested without a terminal")
    void shouldRequireATerminalForClear() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, null, false, true);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("from a terminal");
        assertThat(executor.invocations()).isEmpty();
    }

    /** The three arduino-cli answers a successful run needs: compile, board discovery and upload. */
    private static FakeExecutor cliAnswers() {
        return new FakeExecutor()
                .respond(0, "Sketch uses 1234 bytes")
                .respond(0, """
                        {
                          "detected_ports": [
                            {
                              "matching_boards": [{"name": "Board", "fqbn": "arduino:renesas_uno:unor4wifi"}],
                              "port": {"address": "/dev/cu.usbmodem1"}
                            }
                          ]
                        }""")
                .respond(0, "upload complete");
    }

    /** A terminal with a secret typed at the hidden prompt and a word typed at the visible one. */
    private static SecretMojo.Terminal terminal(String secret, String answer) {
        return new SecretMojo.Terminal() {
            @Override
            public boolean present() {
                return true;
            }

            @Override
            public char[] readSecret(String prompt) {
                return secret.toCharArray();
            }

            @Override
            public String readLine(String prompt) {
                return answer;
            }
        };
    }

    /** A serial conversation in which the board answers {@code boardReplies} and everything sent is kept in {@code sent}. */
    private static SecretMojo.SessionOpener session(String boardReplies, ByteArrayOutputStream sent) {
        return (port, executor) -> new SerialSession(
                new ByteArrayInputStream(boardReplies.getBytes(StandardCharsets.US_ASCII)), sent);
    }

    @Test
    @DisplayName("set uploads, sends the value as hex, reports the store and never prints the value")
    void setStoresTheValueWithoutPrintingIt() throws Exception {
        // Given
        FakeExecutor executor = cliAnswers();
        RecordingLog log = new RecordingLog();
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        SecretMojo mojo = mojo(executor, "wifi.ssid", false, false);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("MyNetwork", ""), session("READY\nOK wifi.ssid\n", sent));

        // When
        mojo.execute();

        // Then
        assertThat(sent.toString(StandardCharsets.US_ASCII))
                .isEqualTo("SET wifi.ssid " + hex("MyNetwork") + "\n");
        assertThat(log.infos()).contains("Stored wifi.ssid in the chip");
        assertThat(String.join("\n", log.infos())).doesNotContain("MyNetwork");
        assertThat(executor.invocations().toString()).doesNotContain("MyNetwork");
    }

    @Test
    @DisplayName("set fails when the board refuses the request, and reports the board's reply")
    void setReportsABoardRefusal() throws Exception {
        // Given
        SecretMojo mojo = mojo(cliAnswers(), "wifi.password", false, false);
        mojo.useTerminalAndSessions(terminal("pass-phrase", ""),
                session("READY\nERR write failed\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("did not store wifi.password: ERR write failed");
    }

    @Test
    @DisplayName("set rejects a value over the Wi-Fi limit before anything is uploaded")
    void setRejectsALongWifiValueBeforeUpload() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.password", false, false);
        mojo.useTerminalAndSessions(terminal("a".repeat(32), ""), session("READY\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("at most 31");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("set fails when no value is typed")
    void setRequiresATypedValue() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.ssid", false, false);
        mojo.useTerminalAndSessions(terminal("", ""), session("READY\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No value was typed");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("list prints each name's state, without values")
    void listPrintsStatesOnly() throws Exception {
        // Given
        RecordingLog log = new RecordingLog();
        SecretMojo mojo = mojo(cliAnswers(), null, true, false);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("", ""), session(
                "READY\nSTATE wifi.ssid written\nSTATE wifi.password missing\nSTATE mailbox.apikey missing\nEND\n",
                new ByteArrayOutputStream()));

        // When
        mojo.execute();

        // Then
        assertThat(log.infos()).contains("STATE wifi.ssid written", "STATE wifi.password missing");
    }

    @Test
    @DisplayName("clear lists the names and clears each one after the word CLEAR is typed")
    void clearAfterConfirmation() throws Exception {
        // Given
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        RecordingLog log = new RecordingLog();
        SecretMojo mojo = mojo(cliAnswers(), null, false, true);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("", "CLEAR"), session(
                "READY\nSTATE wifi.ssid written\nEND\nOK wifi.ssid\nOK wifi.password\nOK mailbox.apikey\n", sent));

        // When
        mojo.execute();

        // Then
        assertThat(sent.toString(StandardCharsets.US_ASCII)).isEqualTo(
                "LIST\nCLEAR wifi.ssid\nCLEAR wifi.password\nCLEAR mailbox.apikey\n");
        assertThat(log.infos()).contains("Cleared mailbox.apikey");
    }

    @Test
    @DisplayName("clear writes nothing and uploads nothing unless CLEAR is typed")
    void clearWithoutConfirmationDoesNothing() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, null, false, true);
        mojo.useTerminalAndSessions(terminal("", "yes"), session("READY\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("CLEAR was not typed");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("clear fails when the board does not confirm a name was cleared")
    void clearReportsABoardFailure() throws Exception {
        // Given
        SecretMojo mojo = mojo(cliAnswers(), null, false, true);
        mojo.useTerminalAndSessions(terminal("", "CLEAR"),
                session("READY\nEND\nERR write failed\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("did not clear wifi.ssid");
    }

    private static String hex(String text) {
        StringBuilder hex = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.US_ASCII)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private SecretMojo mojo(FakeExecutor executor, String key, boolean list, boolean clear) throws Exception {
        SecretMojo mojo = (SecretMojo) lookupEmptyMojo("secret", new File("."));
        MojoConfig config = configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/secret")
                .set("list", String.valueOf(list))
                .set("clear", String.valueOf(clear));
        if (key != null) {
            config.set("key", key);
        }
        mojo = (SecretMojo) configureMojo(mojo, config.build());
        mojo.setLog(new RecordingLog());
        mojo.useExecutor(executor);
        return mojo;
    }
}
