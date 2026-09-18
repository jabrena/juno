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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(output.contains("Entry point: demo.Fixture.main"));
        assertTrue(output.contains("Reachable methods: 1"));
        assertTrue(output.contains("Intrinsics used: [GPIO_PIN_MODE]"));
        assertFalse(output.contains("Juno IR:"), "no --ir flag, so no IR dump");
    }

    @Test
    void inspectWithIrFlagPrintsTheLoweredInstructions() throws Exception {
        compileFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(), "--ir"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Juno IR:"));
        assertTrue(output.contains("IntrinsicCall"));
    }

    @Test
    void inspectWithCfgFlagPrintsBlockTerminators() throws Exception {
        compileFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Fixture", "--classpath", classPath(), "--cfg"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Control flow graphs:"));
        assertTrue(output.contains("block 0 ->"));
    }

    @Test
    void inspectWithRisksPrintsResourceEstimatesAndStructuredFindings() throws Exception {
        compileRiskFixture();

        Main.run(new String[]{"inspect", "--main", "demo.Risky", "--classpath", classPath(), "--risks"});

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Runtime risk analysis:"));
        assertTrue(output.contains("Arena:"));
        assertTrue(output.contains("JUNO-RISK-001"));
        assertTrue(output.contains("JUNO-RISK-005"));
        assertTrue(output.contains("conservative source-level estimates"));
    }

    @Test
    void inspectWithoutMainThrows() {
        assertThrows(CompileException.class, () -> Main.run(new String[]{"inspect"}));
    }

    private String classPath() {
        return temporaryDirectory + File.pathSeparator + "target/classes";
    }

    private void compileFixture() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
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
