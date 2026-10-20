package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Stable generated-code guardrails for the optimization roadmap; no host wall-clock thresholds. */
@DisplayName("Generated-code performance guardrails")
class CompilerPerformanceGuardrailTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Arithmetic benchmark does not regress")
    void shouldNotRegressTheArithmeticBenchmark() throws Exception {
        assertWithinBaseline("ArithmeticBench", """
                package benchmark;
                import io.github.jabrena.juno.hardware.serial.Serial;
                public final class ArithmeticBench {
                    static int run(int iterations) {
                        int result = 0;
                        for (int i = 0; i < iterations; i++) {
                            result += (i * 17) ^ (i >>> 3);
                        }
                        return result;
                    }
                    public static void main(String[] args) {
                        Serial.println(run(100));
                    }
                }
                """, new Baseline(64, 14, 20, 72, 0));
    }

    @Test
    @DisplayName("Array benchmark does not regress")
    void shouldNotRegressTheArrayBenchmark() throws Exception {
        assertWithinBaseline("ArrayBench", """
                package benchmark;
                import io.github.jabrena.juno.hardware.serial.Serial;
                public final class ArrayBench {
                    static int run(int count) {
                        int[] values = new int[16];
                        for (int i = 0; i < count; i++) {
                            values[i] = i * 3 + 1;
                        }
                        int result = 0;
                        for (int i = 0; i < count; i++) {
                            result += values[i];
                        }
                        return result;
                    }
                    public static void main(String[] args) {
                        Serial.println(run(16));
                    }
                }
                """, new Baseline(64, 23, 30, 113, 1));
    }

    @Test
    @DisplayName("Stack benchmark does not regress")
    void shouldNotRegressTheStackBenchmark() throws Exception {
        assertWithinBaseline("StackBench", """
                package benchmark;
                import io.github.jabrena.juno.hardware.serial.Serial;
                public final class StackBench {
                    static int run(int seed) {
                        int a = seed + 1;
                        int b = a + 2;
                        int c = b + 3;
                        int d = c + 4;
                        int e = d + 5;
                        int f = e + 6;
                        int g = f + 7;
                        int h = g + 8;
                        int i = h + 9;
                        int j = i + 10;
                        int k = j + 11;
                        int l = k + 12;
                        return a + b + c + d + e + f + g + h + i + j + k + l;
                    }
                    public static void main(String[] args) {
                        Serial.println(run(1));
                    }
                }
                """, new Baseline(96, 14, 28, 88, 0));
    }

    @Test
    @DisplayName("Allocation benchmark does not regress")
    void shouldNotRegressTheAllocationBenchmark() throws Exception {
        assertWithinBaseline("AllocationBench", """
                package benchmark;
                import io.github.jabrena.juno.hardware.serial.Serial;
                public final class AllocationBench {
                    record Point(int x, int y) {}
                    static int run(int count) {
                        int result = 0;
                        for (int i = 0; i < count; i++) {
                            Point point = new Point(i, i + 1);
                            result += point.x() + point.y();
                        }
                        return result;
                    }
                    public static void main(String[] args) {
                        Serial.println(run(20));
                    }
                }
                """, new Baseline(64, 13, 19, 68, 0));
    }

    private void assertWithinBaseline(String simpleName, String source, Baseline baseline) throws Exception {
        String className = "benchmark." + simpleName;
        CompilerTestSupport.compileJava(temporaryDirectory, className, source);

        CompilationMetrics actual = CompilerTestSupport.compileJuno(temporaryDirectory, className).metrics();

        assertThat(actual.maximumFixedFrameBytes()).as(simpleName + " maximum fixed frame")
                .isLessThanOrEqualTo(baseline.maximumFixedFrameBytes());
        assertThat(actual.loadInstructions()).as(simpleName + " load instructions")
                .isLessThanOrEqualTo(baseline.loadInstructions());
        assertThat(actual.storeInstructions()).as(simpleName + " store instructions")
                .isLessThanOrEqualTo(baseline.storeInstructions());
        assertThat(actual.assemblyInstructions()).as(simpleName + " assembly instructions")
                .isLessThanOrEqualTo(baseline.assemblyInstructions());
        assertThat(actual.directAllocationCalls()).as(simpleName + " direct allocations")
                .isLessThanOrEqualTo(baseline.directAllocationCalls());
    }

    private record Baseline(int maximumFixedFrameBytes, int loadInstructions, int storeInstructions,
                            int assemblyInstructions, int directAllocationCalls) {
    }
}
