package io.github.jabrena.juno;

import io.github.jabrena.juno.analysis.RiskSeverity;
import io.github.jabrena.juno.analysis.RuntimeRisk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code static final} primitive arrays with an all-constant initializer, read only as {@code T[i]} or
 * {@code T.length}: emitted once into {@code .rodata} (flash) instead of being built in the arena by
 * {@code <clinit>}, and kept on the old arena path, with a {@code JUNO-RISK-012} note, when any read could let
 * the array be written. Runtime values of every element type are checked under QEMU ({@code ConstantTables}).
 */
class ConstantTablesTest {
    @TempDir
    Path temporaryDirectory;

    private CompilationResult compile(String className, String source) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo." + className, source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo." + className);
    }

    private static List<RuntimeRisk> tableNotes(CompilationResult result) {
        return result.report().runtimeRisks().findings().stream()
                .filter(finding -> finding.code().equals("JUNO-RISK-012")).toList();
    }

    @Test
    void placesAConstantShortTableInFlashAndSkipsItsArenaInitializer() throws Exception {
        CompilationResult result = compile("Lookup", """
                package demo;
                public final class Lookup {
                    static final short[] RECIPROCAL = {-32768, -300, 0, 300, 32767};
                    static int at(int i) {
                        return RECIPROCAL[i] + RECIPROCAL.length;
                    }
                    public static void main(String[] args) {
                        int total = at(1) + at(3);
                    }
                }
                """);
        String assembly = result.assembly();

        assertThat(assembly).as("the table is read-only data, its values at their native 16-bit width")
                .contains("    .section .rodata\n    .align 2\njuno_table_demo_Lookup_RECIPROCAL__S:\n"
                        + "    .short -32768, -300, 0, 300, 32767\n");
        assertThat(assembly).as("a read loads the table's flash address")
                .contains("ldr r0, =juno_table_demo_Lookup_RECIPROCAL__S");
        assertThat(assembly).as("<clinit> no longer allocates or fills it, and no .bss word holds it")
                .doesNotContain("bl juno_alloc", "juno_static_demo_Lookup_RECIPROCAL");
        assertThat(result.report().runtimeRisks().uncheckedArrayAccesses())
                .as("its length is known, so every access is bounds checked").isZero();
        assertThat(tableNotes(result)).isEmpty();
    }

    @Test
    void emitsEveryElementTypeAtTheWidthArrayLoadsExpect() throws Exception {
        String assembly = compile("Widths", """
                package demo;
                public final class Widths {
                    static final boolean[] FLAGS = {true, false};
                    static final byte[] BYTES = {-1, 127};
                    static final char[] CHARS = {'a', '\\uffff'};
                    static final int[] INTS = {-7, 1 << 20};
                    static final long[] LONGS = {-2L, 1L << 40};
                    static final float[] FLOATS = {1.0f, -0.5f};
                    static final double[] DOUBLES = {1.0};
                    public static void main(String[] args) {
                        int total = (FLAGS[0] ? 1 : 0) + BYTES[0] + CHARS[1] + INTS[1];
                        long wide = LONGS[1] + (long) DOUBLES[0];
                        float real = FLOATS[1];
                    }
                }
                """).assembly();

        assertThat(assembly).contains(
                "juno_table_demo_Widths_FLAGS__Z:\n    .byte 1, 0\n",
                "juno_table_demo_Widths_BYTES__B:\n    .byte -1, 127\n",
                "juno_table_demo_Widths_CHARS__C:\n    .short 97, 65535\n",
                "juno_table_demo_Widths_INTS__I:\n    .word -7, 1048576\n",
                "juno_table_demo_Widths_LONGS__J:\n    .word -2, -1, 0, 256\n",
                "juno_table_demo_Widths_FLOATS__F:\n    .word 1065353216, -1090519040\n",
                "juno_table_demo_Widths_DOUBLES__D:\n    .word 0, 1072693248\n");
        assertThat(assembly).doesNotContain("bl juno_alloc");
    }

    @Test
    void indexesOneTableWithAnotherAndWrapsLongTablesAcrossLines() throws Exception {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            values.append(i == 0 ? "" : ", ").append(i * 3);
        }
        String assembly = compile("Nested", """
                package demo;
                public final class Nested {
                    static final int[] ORDER = {2, 0, 1};
                    static final int[] VALUES = {%s};
                    public static void main(String[] args) {
                        int total = 0;
                        for (int i = 0; i < ORDER.length; i++) total += VALUES[ORDER[i] + 1];
                    }
                }
                """.formatted(values)).assembly();

        assertThat(assembly).contains("ldr r0, =juno_table_demo_Nested_ORDER__I",
                "ldr r0, =juno_table_demo_Nested_VALUES__I",
                "    .word 0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45\n    .word 48, 51, 54, 57\n");
    }

    @Test
    void keepsATableThatCouldBeWrittenInTheArenaAndSaysWhy() throws Exception {
        CompilationResult result = compile("Escapes", """
                package demo;
                public final class Escapes {
                    static final int[] PASSED = {1, 2, 3};
                    static final int[] WRITTEN = {4, 5};
                    static int[] mutable = {6, 7};
                    static final int[] READ = {8, 9};
                    static int sum(int[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    public static void main(String[] args) {
                        WRITTEN[0] = 40;
                        int total = sum(PASSED, 3) + WRITTEN[1] + mutable[0] + READ[1];
                    }
                }
                """);
        String assembly = result.assembly();

        assertThat(assembly).as("only the read-only final table moves to flash")
                .contains("juno_table_demo_Escapes_READ__I:")
                .doesNotContain("juno_table_demo_Escapes_PASSED", "juno_table_demo_Escapes_WRITTEN",
                        "juno_table_demo_Escapes_mutable");
        assertThat(assembly).contains("juno_static_demo_Escapes_PASSED__I:", "juno_static_demo_Escapes_WRITTEN__I:",
                "juno_static_demo_Escapes_mutable__I:", "bl juno_alloc");
        assertThat(tableNotes(result)).extracting(RuntimeRisk::severity).containsOnly(RiskSeverity.INFO);
        assertThat(tableNotes(result)).extracting(RuntimeRisk::message).containsExactlyInAnyOrder(
                "constant table demo.Escapes.PASSED:[I stays in the arena instead of flash: it is used at bytecode "
                        + "offset 7 other than as T[i] or T.length (passed on, stored, written or read across a branch)",
                "constant table demo.Escapes.WRITTEN:[I stays in the arena instead of flash: it is used at bytecode "
                        + "offset 0 other than as T[i] or T.length (passed on, stored, written or read across a branch)");
    }

    @Test
    void leavesArraysWithoutAnAllConstantInitializerUnchanged() throws Exception {
        CompilationResult result = compile("Computed", """
                package demo;
                public final class Computed {
                    static int seed = 3;
                    static final int[] COMPUTED = {seed, seed * 2};
                    static final int[] BUFFER = new int[4];
                    public static void main(String[] args) {
                        BUFFER[0] = COMPUTED[1];
                    }
                }
                """);

        assertThat(result.assembly()).doesNotContain("juno_table_").contains("bl juno_alloc");
        assertThat(tableNotes(result)).as("neither was a constant table candidate").isEmpty();
    }
}
