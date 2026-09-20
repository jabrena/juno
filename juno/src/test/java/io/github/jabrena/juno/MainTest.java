package io.github.jabrena.juno;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void inspectPrintsASummaryReportByDefault() throws Exception {
        compileFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath()});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertThat(output.contains("Entry point: demo.Fixture.main")).isTrue();
        assertThat(output.contains("Reachable methods: 1")).isTrue();
        assertThat(output.contains("Intrinsics used: [GPIO_PIN_MODE]")).isTrue();
        assertThat(output.contains("Juno IR:")).as("no --ir flag, so no IR dump").isFalse();
    }

    @Test
    void inspectWithIrFlagPrintsTheLoweredInstructions() throws Exception {
        compileFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(), "--ir"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertThat(output.contains("Juno IR:")).isTrue();
        assertThat(output.contains("IntrinsicCall")).isTrue();
    }

    @Test
    void inspectWithCfgFlagPrintsBlockTerminators() throws Exception {
        compileFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(), "--cfg"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertThat(output.contains("Control flow graphs:")).isTrue();
        assertThat(output.contains("block 0 ->")).isTrue();
    }

    @Test
    void inspectWithRisksPrintsResourceEstimatesAndStructuredFindings() throws Exception {
        compileRiskFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Risky", "--classpath", classPath(), "--risks"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertThat(output.contains("Runtime risk analysis:")).isTrue();
        assertThat(output.contains("Arena:")).isTrue();
        assertThat(output.contains("JUNO-RISK-001")).isTrue();
        assertThat(output.contains("JUNO-RISK-005")).isTrue();
        assertThat(output.contains("conservative source-level estimates")).isTrue();
    }

    @Test
    void inspectWithoutMainThrows() {
        assertThatThrownBy(() -> Main.run(new String[]{"inspect"})).isInstanceOf(CompileException.class);
    }

    private String classPath() {
        return temporaryDirectory + File.pathSeparator + "target/classes";
    }

    private void compileFixture() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Fixture {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
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
