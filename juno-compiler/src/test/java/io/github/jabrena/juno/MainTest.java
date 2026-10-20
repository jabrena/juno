package io.github.jabrena.juno;


import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Command-line entry point")
class MainTest {
    @TempDir
    Path temporaryDirectory;

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Inspect prints a summary report by default")
    void shouldPrintASummaryReportForInspectByDefault() throws Exception {
        // Given
        compileFixture();

        // When
        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id()});

        // Then
        // main, Gpio's two constructors and pinMode, and the Gpio and NativeGpio static initializers.
        assertThat(captured.toString(StandardCharsets.UTF_8))
                .contains("Entry point: demo.Fixture.main", "Reachable methods: 6", "Intrinsics used: [GPIO_PIN_MODE]")
                .as("no --ir flag, so no IR dump")
                .doesNotContain("Juno IR:");
    }

    @Test
    @DisplayName("Inspect with the IR flag prints the lowered instructions")
    void shouldPrintTheLoweredInstructionsWithTheIrFlag() throws Exception {
        // Given
        compileFixture();

        // When
        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--ir"});

        // Then
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("Juno IR:", "IntrinsicCall");
    }

    @Test
    @DisplayName("Inspect with the CFG flag prints block terminators")
    void shouldPrintBlockTerminatorsWithTheCfgFlag() throws Exception {
        // Given
        compileFixture();

        // When
        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--cfg"});

        // Then
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("Control flow graphs:", "block 0 ->");
    }

    @Test
    @DisplayName("Compile prints resource estimates and structured findings")
    void shouldPrintResourceEstimatesAndStructuredFindingsOnCompile() throws Exception {
        // Given
        compileRiskFixture();
        Path output = temporaryDirectory.resolve("out").resolve("Risky.S");

        // When
        Main.run(new String[]{"compile", "--main", "demo.Risky", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--output", output.toString()});

        // Then
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains(
                "Runtime risk analysis:", "Arena:", "JUNO-RISK-001", "JUNO-RISK-005",
                "conservative source-level estimates");
    }

    @Test
    @DisplayName("Compile with metrics prints deterministic generated-code measurements")
    void shouldPrintDeterministicGeneratedCodeMeasurementsWithMetrics() throws Exception {
        // Given
        compileFixture();
        Path output = temporaryDirectory.resolve("out").resolve("Fixture.S");

        // When
        Main.run(new String[]{"compile", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--output", output.toString(), "--metrics"});

        // Then
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains(
                "Compilation metrics:",
                // main and the Gpio and NativeGpio static initializers; the constructors and pinMode are inlined.
                "Generated methods: 2",
                "Loads / stores:",
                "Maximum fixed frame:",
                "use the final ELF for flash/RAM size and target hardware for cycle counts");
    }

    @Test
    @DisplayName("Inspect without a main class throws")
    void shouldThrowForInspectWithoutAMainClass() {
        // When & Then
        assertThatThrownBy(() -> Main.run(new String[]{"inspect"})).isInstanceOf(CompileException.class);
    }

    @Test
    @DisplayName("The GC log flag emits serial diagnostics in the generated shim")
    void shouldEmitSerialGcDiagnosticsInTheShimWithTheGcLogFlag() throws Exception {
        // Given
        compileFixture();
        Path output = temporaryDirectory.resolve("out").resolve("Fixture.S");

        // When
        Main.run(new String[]{"compile", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--output", output.toString(), "--gc-log"});

        // Then
        String shim = Files.readString(output.resolveSibling("FixtureShim.cpp"), StandardCharsets.UTF_8);
        assertThat(shim).contains("Serial.print(\"[juno-gc] collect: used \")");
    }

    @Test
    @DisplayName("Without the GC log flag the shim omits per-collection diagnostics but keeps the OOM diagnostic")
    void shouldOmitPerCollectionDiagnosticsWithoutTheGcLogFlagButKeepTheOomDiagnostic() throws Exception {
        // Given
        compileFixture();
        Path output = temporaryDirectory.resolve("out").resolve("Fixture.S");

        // When
        Main.run(new String[]{"compile", "--main", "demo.Fixture", "--classpath", classPath(),
                "--board", Board.UNO_R4_WIFI.id(), "--output", output.toString()});

        // Then
        // The per-collection line is opt-in and must be absent; the pre-panic OOM diagnostic is
        // unconditional (a panic is rare/catastrophic, unlike a collection, so it's always emitted
        // regardless of --gc-log) and is expected here either way.
        String shim = Files.readString(output.resolveSibling("FixtureShim.cpp"), StandardCharsets.UTF_8);
        assertThat(shim).doesNotContain("[juno-gc] collect: used").contains("[juno-gc] OOM: need");
    }

    private String classPath() {
        return temporaryDirectory + File.pathSeparator + "target/classes" + File.pathSeparator
                + CompilerTestSupport.JUNO_API_CLASSES;
    }

    private void compileFixture() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                public final class Fixture {
                    public static void main(String[] args) {
                        MCU.getGpio().pinMode(13, Gpio.getOutput());
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Fixture", source);
    }

    private void compileRiskFixture() throws Exception {
        String source = """
                package demo;
                public final class Risky {
                    static final class Box { int value; }
                    static Box create() { return new Box(); }
                    static int divide(int value, int divisor) { return value / divisor; }
                    public static void main() {
                        while (true) {
                            Box box = create();
                            divide(10, box.value);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Risky", source);
    }
}
