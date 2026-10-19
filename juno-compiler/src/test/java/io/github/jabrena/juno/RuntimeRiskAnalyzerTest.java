package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.RuntimeRiskReport;
import io.github.jabrena.juno.board.Board;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Runtime risk analysis findings")
class RuntimeRiskAnalyzerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Reports repeated arena allocation and possible integer division by zero")
    void shouldReportRepeatedArenaAllocationAndPossibleIntegerDivisionByZero() throws Exception {
        // Given
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

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-001", "JUNO-RISK-005");
    }

    @Test
    @DisplayName("Reports the default arena capacity")
    void shouldReportTheDefaultArenaCapacity() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Risky", riskySource());

        // When & Then
        assertThat(report.arenaCapacityBytes()).isEqualTo(8192);
    }

    @Test
    @DisplayName("Flags an unbounded arena allocation inside a loop")
    void shouldFlagAnUnboundedArenaAllocationInsideALoop() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Risky", riskySource());

        // When & Then
        assertThat(report.unboundedArenaAllocation()).isTrue();
    }

    @Test
    @DisplayName("Reports a conservative arena estimate above capacity")
    void shouldReportAConservativeArenaEstimateAboveCapacity() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.TooLarge", """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """);

        // When & Then
        assertThat(report.estimatedArenaBytes()).isGreaterThan(report.arenaCapacityBytes());
    }

    @Test
    @DisplayName("Reports the over-capacity arena estimate with its finding")
    void shouldReportTheOverCapacityArenaEstimateWithItsFinding() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.TooLarge", """
                package demo;
                public final class TooLarge {
                    public static void main() {
                        int[] values = new int[3000];
                        values[0] = 1;
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-002");
    }

    @Test
    @DisplayName("Reports recursion as unbounded call depth")
    void shouldReportRecursionAsUnboundedCallDepth() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Recursive", recursiveSource());

        // When & Then
        assertThat(report.maxCallDepth()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reports recursion as an unbounded stack")
    void shouldReportRecursionAsAnUnboundedStack() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Recursive", recursiveSource());

        // When & Then
        assertThat(report.estimatedMaxStackBytes()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reports recursion with its finding")
    void shouldReportRecursionWithItsFinding() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Recursive", recursiveSource());

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-003");
    }

    @Test
    @DisplayName("Distinguishes generated bounds checks from unchecked parameter access")
    void shouldDistinguishGeneratedBoundsChecksFromUncheckedParameterAccess() throws Exception {
        // Given
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

        // When & Then
        assertThat(report.boundsChecks()).isPositive();
        assertThat(report.uncheckedArrayAccesses()).isPositive();
        assertThat(codes(report)).contains("JUNO-RISK-004");
    }

    @Test
    @DisplayName("Does not warn for known nonzero integer or floating-point divisors")
    void shouldNotWarnForKnownNonzeroIntegerOrFloatingPointDivisors() throws Exception {
        // Given
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

        // When & Then
        assertThat(codes(report))
                .as("floating-point zero division follows Java infinity/NaN semantics and does not panic")
                .doesNotContain("JUNO-RISK-005");
    }

    @Test
    @DisplayName("An uncaught throw is not an allocation loop")
    void shouldNotTreatAnUncaughtThrowAsAnAllocationLoop() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Boom", """
                package demo;
                public final class Boom {
                    public static void main() {
                        throw new IllegalStateException("boom");
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).doesNotContain("JUNO-RISK-001");
    }

    @Test
    @DisplayName("Floor division of a known positive divisor does not warn")
    void shouldNotWarnForFloorDivisionOfAKnownPositiveDivisor() throws Exception {
        // Given
        RuntimeRiskReport safe = compileReport("demo.SafeFloorDiv", """
                package demo;
                public final class SafeFloorDiv {
                    static int bucket(int value) { return Math.floorDiv(value, 4) + Math.floorMod(value, 4); }
                    static long wide(long value) { return Math.floorDiv(value, 3L); }
                    public static void main() {
                        bucket(-9);
                        wide(-9L);
                    }
                }
                """);

        // When & Then
        assertThat(codes(safe)).doesNotContain("JUNO-RISK-005");
    }

    @Test
    @DisplayName("Floor division of an unknown divisor warns")
    void shouldWarnForFloorDivisionOfAnUnknownDivisor() throws Exception {
        // Given
        RuntimeRiskReport risky = compileReport("demo.RiskyFloorDiv", """
                package demo;
                public final class RiskyFloorDiv {
                    static int bucket(int value, int size) { return Math.floorMod(value, size); }
                    public static void main() {
                        bucket(-9, 4);
                    }
                }
                """);

        // When & Then
        assertThat(codes(risky)).contains("JUNO-RISK-005");
    }

    @Test
    @DisplayName("Reports a dereference of a compile-time null")
    void shouldReportADereferenceOfACompileTimeNull() throws Exception {
        // Given
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

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-006");
    }

    @Test
    @DisplayName("Reports a store through a compile-time null receiver")
    void shouldReportAStoreThroughACompileTimeNullReceiver() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.NullStoreReceiver", """
                package demo;
                public final class NullStoreReceiver {
                    static final class Box { int value; }
                    public static void main() {
                        Box box = null;
                        box.value = 5;
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-006");
    }

    @Test
    @DisplayName("Reports a compile-time null array load")
    void shouldReportACompileTimeNullArrayLoad() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.NullArrayLoad", """
                package demo;
                public final class NullArrayLoad {
                    public static void main() {
                        int[] values = null;
                        int ignored = values[0];
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-006");
    }

    @Test
    @DisplayName("Reports a compile-time null array store")
    void shouldReportACompileTimeNullArrayStore() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.NullArrayStore", """
                package demo;
                public final class NullArrayStore {
                    public static void main() {
                        int[] values = null;
                        values[0] = 1;
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-006");
    }

    @Test
    @DisplayName("Reports long division by a literal zero divisor")
    void shouldReportLongDivisionByALiteralZeroDivisor() throws Exception {
        // Given
        RuntimeRiskReport literalZero = compileReport("demo.LiteralZeroLongDiv", """
                package demo;
                public final class LiteralZeroLongDiv {
                    static long divide(long value) { return value / 0L; }
                    public static void main() { divide(10L); }
                }
                """);

        // When & Then
        assertThat(codes(literalZero)).contains("JUNO-RISK-005");
    }

    @Test
    @DisplayName("Reports long division by an unknown divisor")
    void shouldReportLongDivisionByAnUnknownDivisor() throws Exception {
        // Given
        RuntimeRiskReport unknownDivisor = compileReport("demo.UnknownLongDivisor", """
                package demo;
                public final class UnknownLongDivisor {
                    static long divide(long value, long divisor) { return value / divisor; }
                    public static void main() { divide(10L, 4L); }
                }
                """);

        // When & Then
        assertThat(codes(unknownDivisor)).contains("JUNO-RISK-005");
    }

    @Test
    @DisplayName("Long division by a known nonzero divisor does not warn")
    void shouldNotWarnForLongDivisionByAKnownNonzeroDivisor() throws Exception {
        // Given
        RuntimeRiskReport safe = compileReport("demo.SafeLongDiv", """
                package demo;
                public final class SafeLongDiv {
                    static long divide(long value) { return value / 4L; }
                    public static void main() { divide(10L); }
                }
                """);

        // When & Then
        assertThat(codes(safe)).doesNotContain("JUNO-RISK-005");
    }

    @Test
    @DisplayName("Reports a StringBuilder capacity that can overflow the runtime string slot")
    void shouldReportAStringBuilderCapacityThatCanOverflowTheRuntimeStringSlot() throws Exception {
        // Given
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

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-007");
    }

    @Test
    @DisplayName("Does not report a StringBuilder capacity well within the runtime string slot")
    void shouldNotReportAStringBuilderCapacityWellWithinTheRuntimeStringSlot() throws Exception {
        // Given
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

        // When & Then
        assertThat(codes(report))
                .as("a capacity comfortably under the string-slot size can never overflow it")
                .doesNotContain("JUNO-RISK-007");
    }

    @Test
    @DisplayName("Warns when subtasks are forked in a loop or beyond the slot limit")
    void shouldWarnWhenSubtasksAreForkedInALoopOrBeyondTheSlotLimit() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Spawner", """
                package demo;
                public final class Spawner {
                    public static void main() throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            for (int i = 0; i < 2; i++) {
                                scope.fork(() -> { });
                            }
                            scope.fork(() -> { });
                            scope.fork(() -> { });
                            scope.fork(() -> { });
                            scope.fork(() -> { });
                            scope.join();
                        }
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-008", "JUNO-RISK-009");
    }

    @Test
    @DisplayName("Does not warn for joined subtasks within the slot limit")
    void shouldNotWarnForJoinedSubtasksWithinTheSlotLimit() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Joined", """
                package demo;
                public final class Joined {
                    public static void main() throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(() -> { });
                            scope.fork(() -> { });
                            scope.join();
                        }
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(() -> { });
                            scope.join();
                        }
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).doesNotContain("JUNO-RISK-008", "JUNO-RISK-009", "JUNO-RISK-010", "JUNO-RISK-011");
    }

    @Test
    @DisplayName("Warns when a subtask body nests scopes or recurses")
    void shouldWarnWhenASubtaskBodyNestsScopesOrRecurses() throws Exception {
        // Given
        RuntimeRiskReport report = compileReport("demo.Nested", """
                package demo;
                public final class Nested {
                    static int fib(int n) { return n < 2 ? n : fib(n - 1) + fib(n - 2); }
                    public static void main() throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                try (var inner = java.util.concurrent.StructuredTaskScope.open()) {
                                    inner.fork(() -> { });
                                    inner.join();
                                } catch (Exception failure) {
                                    throw new IllegalStateException();
                                }
                            });
                            scope.fork(() -> { fib(10); });
                            scope.join();
                        }
                    }
                }
                """);

        // When & Then
        assertThat(codes(report)).contains("JUNO-RISK-010", "JUNO-RISK-011");
    }

    private static String riskySource() {
        return """
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
    }

    private static String recursiveSource() {
        return """
                package demo;
                public final class Recursive {
                    static int countDown(int value) {
                        if (value == 0) return 0;
                        return countDown(value - 1);
                    }
                    public static void main() { countDown(3); }
                }
                """;
    }

    private RuntimeRiskReport compileReport(String className, String source) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        CompilationResult result = new JunoCompiler().compile(
                new CompilationRequest(CompilerTestSupport.classpath(temporaryDirectory), className, false,
                        Optional.of(Board.UNO_R4_WIFI.id())));
        return result.report().runtimeRisks();
    }

    private static List<String> codes(RuntimeRiskReport report) {
        return report.findings().stream().map(finding -> finding.code()).toList();
    }
}
