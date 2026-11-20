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
import java.nio.file.Files;
import java.nio.file.Path;

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
        SecretMojo mojo = mojo(executor, null, null, false);

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
        SecretMojo mojo = mojo(executor, "wifi.ssid", "wifi.ssid", false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("Choose exactly one");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("Fails before any command when a value is requested without a terminal")
    void shouldRequireATerminalForValues() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.ssid", null, false);

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
        SecretMojo mojo = mojo(executor, null, null, true);

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
        SecretMojo mojo = mojo(executor, "wifi.ssid", null, false);
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
        SecretMojo mojo = mojo(cliAnswers(), "wifi.password", null, false);
        mojo.useTerminalAndSessions(terminal("pass-phrase", ""),
                session("READY\nERR write failed\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("did not store wifi.password: ERR write failed");
    }

    @Test
    @DisplayName("set rejects a value too long to share a record with its name before anything is uploaded")
    void setRejectsALongValueBeforeUpload() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.password", null, false);
        mojo.useTerminalAndSessions(terminal("a".repeat(50), ""), session("READY\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("at most 49");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("set fails when no value is typed")
    void setRequiresATypedValue() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, "wifi.ssid", null, false);
        mojo.useTerminalAndSessions(terminal("", ""), session("READY\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("No value was typed");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("list asks about each name the user gives, and prints its state without a value")
    void listPrintsStatesOnly() throws Exception {
        // Given
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        RecordingLog log = new RecordingLog();
        SecretMojo mojo = mojo(cliAnswers(), null, "wifi.ssid, wifi.password", false);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("", ""), session(
                "READY\nSTATE wifi.ssid written\nSTATE wifi.password missing\n", sent));

        // When
        mojo.execute();

        // Then
        assertThat(sent.toString(StandardCharsets.US_ASCII)).isEqualTo("LIST wifi.ssid\nLIST wifi.password\n");
        assertThat(log.infos()).contains("STATE wifi.ssid written", "STATE wifi.password missing");
    }

    @Test
    @DisplayName("list fails when the board answers about a different name")
    void listRejectsAnUnexpectedReply() throws Exception {
        // Given
        SecretMojo mojo = mojo(cliAnswers(), null, "wifi.ssid", false);
        mojo.useTerminalAndSessions(terminal("", ""),
                session("READY\nERR missing name\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("did not report wifi.ssid");
    }

    @Test
    @DisplayName("list given true or false fails before any command, with how to name the keys")
    void listRejectsTheOldSwitch() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, null, "true", false);

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("-Djuno.secret.list=wifi.ssid,wifi.password");
        assertThat(executor.invocations()).isEmpty();
    }

    @Test
    @DisplayName("without -Djuno.main the plugin compiles and uploads its own SecretProvisioner")
    void usesTheBundledProvisionerByDefault() throws Exception {
        // Given
        RecordingLog log = new RecordingLog();
        FakeExecutor executor = cliAnswers();
        SecretMojo mojo = (SecretMojo) lookupEmptyMojo("secret", new File("."));
        mojo = (SecretMojo) configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/secret-bundled")
                .set("list", "wifi.ssid")
                .set("clear", "false")
                .build());
        mojo.setLog(log);
        mojo.useExecutor(executor);
        mojo.useTerminalAndSessions(terminal("", ""),
                session("READY\nSTATE wifi.ssid missing\n", new ByteArrayOutputStream()));

        // When
        mojo.execute();

        // Then
        Path sketch = Path.of("target/juno-mojo-test/secret-bundled/SecretProvisionerAsm");
        assertThat(sketch.resolve("SecretProvisioner.S")).exists();
        assertThat(log.infos()).anyMatch(line -> line.contains("using the plugin's own "
                + "io.github.jabrena.juno.maven.provisioner.SecretProvisioner"));
        assertThat(executor.invocations().get(0).arguments()).contains("compile", sketch.toAbsolutePath().toString());
    }

    @Test
    @DisplayName("clear empties every data slot after the word CLEAR is typed")
    void clearAfterConfirmation() throws Exception {
        // Given
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        RecordingLog log = new RecordingLog();
        SecretMojo mojo = mojo(cliAnswers(), null, null, true);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("", "CLEAR"), session(
                "READY\nOK CLEAR ALL\n",
                sent));

        // When
        mojo.execute();

        // Then
        assertThat(sent.toString(StandardCharsets.US_ASCII)).isEqualTo("CLEAR ALL\n");
        assertThat(log.infos()).contains("Emptied every data slot on the chip");
    }

    @Test
    @DisplayName("clear writes nothing and uploads nothing unless CLEAR is typed")
    void clearWithoutConfirmationDoesNothing() throws Exception {
        // Given
        FakeExecutor executor = new FakeExecutor();
        SecretMojo mojo = mojo(executor, null, null, true);
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
        SecretMojo mojo = mojo(cliAnswers(), null, null, true);
        mojo.useTerminalAndSessions(terminal("", "CLEAR"),
                session("READY\nERR write failed\n", new ByteArrayOutputStream()));

        // When & Then
        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("did not empty every data slot");
    }

    @Test
    @DisplayName("a READY the board repeated while the request was on its way is skipped, not taken as the reply")
    void skipsRepeatedReady() throws Exception {
        // Given
        RecordingLog log = new RecordingLog();
        SecretMojo mojo = mojo(cliAnswers(), "wifi.ssid", null, false);
        mojo.setLog(log);
        mojo.useTerminalAndSessions(terminal("MyNetwork", ""),
                session("READY\nREADY\nREADY\nOK wifi.ssid\n", new ByteArrayOutputStream()));

        // When
        mojo.execute();

        // Then
        assertThat(log.infos()).contains("Stored wifi.ssid in the chip");
    }

    @Test
    @DisplayName("on a Pico 2 W the provisioning program is uploaded from the build that holds the secure firmware")
    void picoUploadsFromTheSecureFirmwareBuild() throws Exception {
        // Given: arduino-cli is faked, so the UF2 it would build is put where it would leave it
        Path sketch = Path.of("target/juno-mojo-test/secret-pico/PicoSecretProgramAsm").toAbsolutePath().normalize();
        Path uf2 = sketch.resolve("build/PicoSecretProgramAsm.ino.uf2");
        Files.createDirectories(uf2.getParent());
        Files.write(uf2, PicoSecureFirmwareInstallerTest.PicoUf2.of(49));
        FakeExecutor executor = new FakeExecutor()
                .respond(0, "Sketch uses 1234 bytes")
                .respond(0, """
                        {
                          "detected_ports": [
                            {
                              "matching_boards": [{"name": "Pico 2W", "fqbn": "rp2040:rp2040:rpipico2w"}],
                              "port": {"address": "/dev/cu.usbmodem1201"}
                            }
                          ]
                        }""")
                .respond(0, "upload complete");
        SecretMojo mojo = (SecretMojo) lookupEmptyMojo("secret", new File("."));
        mojo = (SecretMojo) configureMojo(mojo, configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.PicoSecretProgram")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .set("board", "raspberry-pi-pico-2-w")
                .set("outputDirectory", "target/juno-mojo-test/secret-pico")
                .set("list", "wifi.ssid")
                .set("clear", "false")
                .build());
        mojo.setLog(new RecordingLog());
        mojo.useExecutor(executor);
        mojo.useTerminalAndSessions(terminal("", ""), session("READY\nSTATE wifi.ssid missing\n", new ByteArrayOutputStream()));

        // When
        mojo.execute();

        // Then: a plain upload would reset the board into BOOTSEL and find no UF2, leaving it there
        assertThat(executor.invocations().get(2).arguments()).contains("upload", "--port", "/dev/cu.usbmodem1201",
                "--input-dir", sketch.resolve("build").toString());
    }

    private static String hex(String text) {
        StringBuilder hex = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.US_ASCII)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private SecretMojo mojo(FakeExecutor executor, String key, String list, boolean clear) throws Exception {
        SecretMojo mojo = (SecretMojo) lookupEmptyMojo("secret", new File("."));
        MojoConfig config = configuration()
                .set("arduinoCli", "arduino-cli")
                .set("mainClass", "io.github.jabrena.juno.maven.fixture.TinyProgram")
                .list("classpathElements", "classpathElement", TinyProgramClasspath.elements())
                .set("board", "arduino-uno-r4-wifi")
                .set("outputDirectory", "target/juno-mojo-test/secret")
                .set("clear", String.valueOf(clear));
        if (key != null) {
            config.set("key", key);
        }
        if (list != null) {
            config.set("list", list);
        }
        mojo = (SecretMojo) configureMojo(mojo, config.build());
        mojo.setLog(new RecordingLog());
        mojo.useExecutor(executor);
        return mojo;
    }
}
