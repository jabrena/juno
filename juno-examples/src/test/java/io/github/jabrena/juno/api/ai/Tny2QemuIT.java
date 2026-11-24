package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.tny2.Tny2Inference;
import io.github.jabrena.juno.tny2.Tny2Reader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the board's TNY2 engine without a board: {@link Tny2QemuProgram} is compiled by Juno for the Nano ESP32, built by
 * its Arduino core and booted under Espressif's QEMU in the Xtensa harness image of {@code juno-compiler}, and every
 * answer it prints must match the Java engine reading the bundled JUNO.BIN that {@code begin()} links in. That checks the C++ engine's
 * normalization, lookahead and fallback against the reference, including the model copy into PSRAM. QEMU has no
 * Wi-Fi, so the web chat is out of scope. Opt-in: {@code ./mvnw -f juno-examples/pom.xml -Pxtensa verify}; skipped
 * without Docker.
 */
@Tag("xtensa")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("TNY2 engine under QEMU")
class Tny2QemuIT {
    private static final Path BASEDIR = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
    private static final Path HARNESS = BASEDIR.resolve("../juno-compiler/src/test/docker/xtensa").normalize();
    private static final String BOARD = "arduino-nano-esp32";
    private static final String NAME = "Tny2QemuProgram";
    /** The model {@code LanguageModel.begin()} links in, from juno-tny2. */
    private static final String BUNDLED_MODEL = "/tny2/JUNO.BIN";
    private static final String BEGIN_MARKER = "[juno-begin]\n";
    private static final String EXIT_MARKER = "[juno-exit]\n";
    private static final int RUN_TIMEOUT_SECONDS = Integer.getInteger("juno.xtensaRunTimeoutSeconds", 900);

    @Container
    private static final GenericContainer<?> XTENSA = container();

    @Test
    @DisplayName("Answers every question exactly as the Java engine does")
    void shouldAnswerAsTheJavaEngine() throws Exception {
        // Given
        Path sketch = BASEDIR.resolve("target/tny2-qemu").resolve(NAME);
        Files.createDirectories(sketch);
        CompilationResult result = new JunoCompiler().compileTo(
                List.of(programOnly(), codeSource(Tny2Status.class), codeSource(MCU.class)),
                Tny2QemuProgram.class.getName(), sketch.resolve("program.S"), sketch.resolve("shim.cpp"), false,
                Optional.of(BOARD));
        Files.writeString(sketch.resolve(NAME + ".ino"), harnessSketch(result.entryPointSymbol()));

        // When
        String transcript = runUnderQemu(sketch);

        // Then
        assertThat(transcript).isEqualTo(expectedTranscript());
    }

    /** What the board must print: the load status, then each question with the Java engine's answer. */
    private static String expectedTranscript() throws IOException {
        Tny2Inference engine;
        try (InputStream model = Tny2QemuIT.class.getResourceAsStream(BUNDLED_MODEL)) {
            assertThat(model).as("the bundled model %s on the test classpath", BUNDLED_MODEL).isNotNull();
            engine = new Tny2Inference(new Tny2Reader().read(model.readAllBytes()));
        }
        StringBuilder expected = new StringBuilder("load OK\n");
        for (String question : Tny2QemuProgram.QUESTIONS) {
            expected.append(question).append(" -> ").append(engine.answer(question)).append('\n');
        }
        return expected.append(EXIT_MARKER).toString();
    }

    private static String runUnderQemu(Path sketch) throws IOException, InterruptedException {
        String work = "/work/" + BOARD + "/" + NAME;
        XTENSA.execInContainer("mkdir", "-p", work);
        for (String file : List.of("program.S", "shim.cpp", NAME + ".ino")) {
            XTENSA.copyFileToContainer(MountableFile.forHostPath(sketch.resolve(file)), work + "/" + file);
        }
        ExecResult run = XTENSA.execInContainer("timeout", "-k", "10", String.valueOf(RUN_TIMEOUT_SECONDS),
                "/harness/run.sh", work, BOARD);
        String uart = run.getStdout().replace("\r\n", "\n");
        assertThat(run.getExitCode()).as("run.sh exit; output:%n%s%s", uart, run.getStderr()).isZero();
        int begin = uart.indexOf(BEGIN_MARKER);
        int end = uart.indexOf(EXIT_MARKER, Math.max(begin, 0));
        assertThat(begin).as("program start marker; output:%n%s", uart).isNotNegative();
        assertThat(end).as("program exit marker; output:%n%s", uart).isNotNegative();
        return uart.substring(begin + BEGIN_MARKER.length(), end + EXIT_MARKER.length());
    }

    /** The same wrapper XtensaRunIT uses: Serial on UART0 before the first byte, and markers around the program. */
    private static String harnessSketch(String entryPointSymbol) {
        return """
                // Generated by Tny2QemuIT. Do not edit.
                extern "C" void %1$s();

                void setup() {
                  Serial.begin(115200);
                  Serial.print("[juno-begin]\\n");
                  %1$s();
                  Serial.print("[juno-exit]\\n");
                  Serial.flush();
                }

                void loop() {
                }
                """.formatted(entryPointSymbol);
    }

    /**
     * A directory holding only {@link Tny2QemuProgram}'s class: test-classes also holds this module's test doubles of
     * {@code Serial}, {@code Delay} and others, which Juno must not link instead of the real API.
     */
    private static Path programOnly() throws IOException {
        String file = Tny2QemuProgram.class.getName().replace('.', '/') + ".class";
        Path target = BASEDIR.resolve("target/tny2-qemu/classes").resolve(file);
        Files.createDirectories(target.getParent());
        Files.copy(codeSource(Tny2QemuIT.class).resolve(file), target, StandardCopyOption.REPLACE_EXISTING);
        return BASEDIR.resolve("target/tny2-qemu/classes");
    }

    private static Path codeSource(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static GenericContainer<?> container() {
        String prebuilt = System.getProperty("juno.xtensaImage", "");
        if (!prebuilt.isBlank()) {
            return new GenericContainer<>(DockerImageName.parse(prebuilt));
        }
        return new GenericContainer<>(new ImageFromDockerfile("juno-xtensa:bookworm-v1", false)
                .withFileFromPath(".", HARNESS));
    }
}
