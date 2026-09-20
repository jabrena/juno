package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.RuntimeRiskReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRiskAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsRepeatedArenaAllocationAndPossibleIntegerDivisionByZero() throws Exception {
        RuntimeRiskReport report = compileReport("demo.Risky", """
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
                """);

        assertThat(report.arenaCapacityBytes()).isEqualTo(8192);
        assertThat(report.estimatedArenaBytes() > 0).isTrue();
        assertThat(report.unboundedArenaAllocation()).isTrue();
        assertThat(hasCode(report, "JUNO-RISK-001")).isTrue();
        assertThat(hasCode(report, "JUNO-RISK-005")).isTrue();
    }

    @Test
    void reportsAConservativeArenaEstimateAboveCapacity() throws Exception {
        RuntimeRiskReport report = compileReport("demo.TooLarge", """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """);

        assertThat(report.estimatedArenaBytes() > report.arenaCapacityBytes()).isTrue();
        assertThat(hasCode(report, "JUNO-RISK-002")).isTrue();
    }

    @Test
    void reportsRecursionAsUnboundedCallDepthAndStack() throws Exception {
        RuntimeRiskReport report = compileReport("demo.Recursive", """
                package demo;
                public final class Recursive {
                    static int countDown(int value) {
                        if (value == 0) return 0;
                        return countDown(value - 1);
                    }
                    public static void main() { countDown(3); }
                }
                """);

        assertThat(report.maxCallDepth()).isEqualTo(-1);
        assertThat(report.estimatedMaxStackBytes()).isEqualTo(-1);
        assertThat(hasCode(report, "JUNO-RISK-003")).isTrue();
    }

    @Test
    void distinguishesGeneratedBoundsChecksFromUncheckedParameterAccess() throws Exception {
        RuntimeRiskReport report = compileReport("demo.Arrays", """
                package demo;
                public final class Arrays {
                    static int read(int[] values, int index) { return values[index]; }
                    public static void main() {
                        int[] values = new int[2];
                        values[0] = 7;
                        read(values, 0);
                    }
                }
                """);

        assertThat(report.boundsChecks() > 0).isTrue();
        assertThat(report.uncheckedArrayAccesses() > 0).isTrue();
        assertThat(hasCode(report, "JUNO-RISK-004")).isTrue();
    }

    @Test
    void doesNotWarnForKnownNonzeroIntegerOrFloatingPointDivisors() throws Exception {
        RuntimeRiskReport report = compileReport("demo.SafeDivision", """
                package demo;
                public final class SafeDivision {
                    static int half(int value) { return value / 2; }
                    static float ratio(float left, float right) { return left / right; }
                    public static void main() {
                        half(4);
                        ratio(1.0f, 0.0f);
                    }
                }
                """);

        assertThat(hasCode(report, "JUNO-RISK-005")).as("floating-point zero division follows Java infinity/NaN semantics and does not panic").isFalse();
    }

    @Test
    void reportsADereferenceOfCompileTimeNull() throws Exception {
        RuntimeRiskReport report = compileReport("demo.NullReceiver", """
                package demo;
                public final class NullReceiver {
                    static final class Box { int value; }
                    public static void main() {
                        Box box = null;
                        int ignored = box.value;
                    }
                }
                """);

        assertThat(hasCode(report, "JUNO-RISK-006")).isTrue();
    }

    @Test
    void reportsAStringBuilderCapacityThatCanOverflowTheRuntimeStringSlotAtToString() throws Exception {
        RuntimeRiskReport report = compileReport("demo.OversizedBuilder", """
                package demo;
                public final class OversizedBuilder {
                    public static void main() {
                        StringBuilder builder = new StringBuilder(40);
                        builder.append('a');
                        String ignored = builder.toString();
                    }
                }
                """);

        assertThat(hasCode(report, "JUNO-RISK-007")).isTrue();
    }

    @Test
    void doesNotReportAStringBuilderCapacityWellWithinTheRuntimeStringSlot() throws Exception {
        RuntimeRiskReport report = compileReport("demo.SafeBuilder", """
                package demo;
                public final class SafeBuilder {
                    public static void main() {
                        StringBuilder builder = new StringBuilder(8);
                        builder.append('a');
                        String ignored = builder.toString();
                    }
                }
                """);

        assertThat(hasCode(report, "JUNO-RISK-007"))
                .as("a capacity comfortably under the string-slot size can never overflow it")
                .isFalse();
    }

    private RuntimeRiskReport compileReport(String className, String source) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, className, source);
        CompilationResult result = new JunoCompiler().compile(
                new CompilationRequest(List.of(temporaryDirectory, Path.of("target/classes")), className));
        return result.report().runtimeRisks();
    }

    private boolean hasCode(RuntimeRiskReport report, String code) {
        return report.findings().stream().anyMatch(finding -> finding.code().equals(code));
    }
}
