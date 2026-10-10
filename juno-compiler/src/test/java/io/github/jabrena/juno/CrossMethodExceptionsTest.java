package io.github.jabrena.juno;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exceptions that cross method boundaries: a throwing callee leaves the exception pending and returns, and the
 * caller's poll routes it to a {@code catch}/{@code finally} handler or keeps unwinding. Also try-with-resources,
 * whose {@code close()} and {@code addSuppressed} calls ride on the same mechanism.
 */
class CrossMethodExceptionsTest {
    @TempDir
    Path temporaryDirectory;

    private static final String CALLEE_THROWS = """
            package demo;
            import io.github.jabrena.juno.api.io.serial.Serial;
            public final class Crossing {
                static int parse(int value) {
                    if (value < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                    return value * 2;
                }
                static int middle(int value) {
                    return parse(value) + 1;
                }
                static int quiet(int value) {
                    return value + 1;
                }
                public static void main(String[] args) {
                    try {
                        Serial.println(middle(-1));
                        Serial.println(quiet(3));
                    } catch (IllegalArgumentException e) {
                        Serial.println(e.getMessage());
                    }
                }
            }
            """;

    @Test
    void aCallerCatchesWhatItsCalleeThrows() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);

        IrProgram program = optimized("demo.Crossing");

        IrMethod parse = method(program, "parse");
        assertThat(count(parse, Intrinsic.THROW_RAISE)).as("no handler here: raise and unwind").isEqualTo(1);
        assertThat(parse.blocks()).anyMatch(block -> block.terminator() instanceof IrTerminator.Return returned
                && returned.value().isEmpty() && endsInRaise(block));

        IrMethod middle = method(program, "middle");
        assertThat(count(middle, Intrinsic.THROW_PENDING)).as("polls after calling parse").isEqualTo(1);
        assertThat(count(middle, Intrinsic.THROW_CATCH)).as("no handler: just keep unwinding").isZero();

        IrMethod main = method(program, "main");
        assertThat(count(main, Intrinsic.THROW_PENDING)).as("only the call to middle can throw").isEqualTo(1);
        assertThat(count(main, Intrinsic.THROW_CATCH)).isEqualTo(1);
        assertThat(count(main, Intrinsic.THROWABLE_GET_MESSAGE)).isEqualTo(1);

        // quiet() is small enough to be inlined into its caller; how it was lowered is what matters here.
        assertThat(count(method(lowered("demo.Crossing"), "quiet"), Intrinsic.THROW_PENDING)).isZero();
    }

    @Test
    void programsThatNeverThrowAcrossACallAreNotPolled() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Quiet {
                    static int twice(int value) { return value * 2; }
                    public static void main(String[] args) {
                        try {
                            Serial.println(twice(4));
                        } catch (RuntimeException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Quiet", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Quiet");

        assertThat(result.assembly()).doesNotContain("juno_throw_pending", "juno_throw_check_escape");
    }

    @Test
    void anUnmatchedExceptionKeepsUnwindingPastTheCatchingFrame() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Partial {
                    static void fail(int kind) {
                        if (kind == 0) {
                            throw new IllegalArgumentException("argument");
                        }
                        throw new IllegalStateException("state");
                    }
                    static void guarded(int kind) {
                        try {
                            fail(kind);
                        } catch (IllegalArgumentException e) {
                            Serial.println("wrong handler");
                        }
                    }
                    public static void main(String[] args) {
                        try {
                            guarded(1);
                        } catch (IllegalStateException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Partial", source);

        IrMethod guarded = method(optimized("demo.Partial"), "guarded");

        // guarded() has a handler, but only for IllegalArgumentException: IllegalStateException funnels into
        // the shared propagation block instead of panicking.
        assertThat(count(guarded, Intrinsic.THROW_CATCH)).isEqualTo(1);
        assertThat(guarded.blocks()).anyMatch(block -> block.terminator() instanceof IrTerminator.Switch switched
                && switched.defaultTarget() != switched.targets().get(0)
                && isReturnBlock(guarded, switched.defaultTarget()));
    }

    @Test
    void aFinallyBlockRunsWhenACalleeThrowsAndThenRethrows() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Cleanup {
                    static void fail() { throw new IllegalStateException("boom"); }
                    static void work() {
                        try {
                            fail();
                        } finally {
                            Serial.println("cleanup");
                        }
                    }
                    public static void main(String[] args) {
                        try {
                            work();
                        } catch (IllegalStateException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Cleanup", source);

        IrProgram program = optimized("demo.Cleanup");
        IrMethod work = method(program, "work");

        assertThat(count(work, Intrinsic.THROW_CATCH)).as("fail() lands in finally's catch-any").isEqualTo(1);
        assertThat(count(work, Intrinsic.THROW_RAISE)).as("finally rethrows to work()'s caller").isEqualTo(1);
        assertThat(count(work, Intrinsic.SERIAL_PRINTLN_STRING)).as("javac inlines finally on both paths")
                .isEqualTo(2);
        assertThat(count(method(program, "main"), Intrinsic.THROW_CATCH)).isEqualTo(1);
    }

    private static final String TRY_WITH_RESOURCES = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoUnoQ;
            import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.api.io.serial.Serial;
            @Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
            public final class Resources {
                static final class Pin implements AutoCloseable {
                    final int number;
                    Pin(int number) { this.number = number; }
                    @Override
                    public void close() { Serial.println(number); }
                }
                static void use(int value) {
                    try (Pin pin = new Pin(value)) {
                        if (value < 0) {
                            throw new IllegalArgumentException("negative");
                        }
                        Serial.println("used");
                    }
                }
                public static void main(String[] args) {
                    try {
                        use(1);
                        use(-1);
                    } catch (IllegalArgumentException e) {
                        Serial.println(e.getMessage());
                    }
                }
            }
            """;

    @Test
    void tryWithResourcesClosesTheResourceOnBothPathsAndRethrows() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources",
                TRY_WITH_RESOURCES.replace("@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})\n", ""));

        IrProgram program = optimized("demo.Resources");
        IrMethod use = method(program, "use");

        // close() is called on the normal path and on the exceptional path, where the original exception is
        // then rethrown (addSuppressed is dropped; Juno keeps no suppressed list).
        long closes = use.blocks().stream().flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrInstruction.Call call
                        && call.method().name().equals("close"))
                .count();
        assertThat(closes).isGreaterThanOrEqualTo(2);
        assertThat(count(use, Intrinsic.THROW_RAISE)).isGreaterThanOrEqualTo(1);
        assertThat(count(method(program, "main"), Intrinsic.THROW_CATCH)).as("one poll per use() call").isEqualTo(2);
    }

    @Test
    void generatesTheSameUnwindingMachineryForBothBoards() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources", TRY_WITH_RESOURCES);
        List<Path> classpath = CompilerTestSupport.classpath(temporaryDirectory);
        JunoCompiler juno = new JunoCompiler();

        for (String board : List.of("arduino-uno-r4-wifi", "arduino-uno-q")) {
            CompilationResult result = juno.compile(classpath, "demo.Resources", false, Optional.of(board));

            assertThat(result.assembly()).as(board).contains("bl juno_throw_pending", "bl juno_throw_catch",
                    "bl juno_throw_raise", "bl juno_throw_check_escape");
            assertThat(result.runtimeShim()).as(board).contains(
                    "extern \"C\" int32_t juno_throw_catch(int32_t caughtMask)",
                    "extern \"C\" void juno_throw_check_escape()");
        }
    }

    @Test
    void aDivisionByZeroInACalleeIsCatchableByItsCaller() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Divide {
                    static int ratio(int total, int count) {
                        return total / count;
                    }
                    public static void main(String[] args) {
                        try {
                            Serial.println(ratio(10, 2));
                            Serial.println(ratio(10, 0));
                        } catch (ArithmeticException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Divide", source);

        IrProgram program = optimized("demo.Divide");

        assertThat(count(method(program, "ratio"), Intrinsic.THROW_RAISE)).as("ratio unwinds on a zero divisor")
                .isEqualTo(1);
        assertThat(count(method(program, "main"), Intrinsic.THROW_CATCH)).isEqualTo(2);
    }

    @Test
    void aProgramWithNoArithmeticHandlerStillPanicsOnDivisionByZero() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Panics {
                    static int ratio(int total, int count) {
                        return total / count;
                    }
                    public static void main(String[] args) {
                        Serial.println(ratio(10, 2));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Panics", source);

        // ratio() is small enough to be inlined into main; how it was lowered is what matters here.
        assertThat(count(method(lowered("demo.Panics"), "ratio"), Intrinsic.THROW_RAISE)).isZero();
    }

    private static boolean endsInRaise(IrBasicBlock block) {
        return block.instructions().stream().anyMatch(instruction ->
                instruction instanceof IrInstruction.IntrinsicCall call && call.intrinsic() == Intrinsic.THROW_RAISE);
    }

    private static boolean isReturnBlock(IrMethod method, int start) {
        return method.blocks().stream().anyMatch(block -> block.start() == start
                && block.terminator() instanceof IrTerminator.Return);
    }

    private IrProgram lowered(String mainClass) {
        return new CompilationPipeline().lower(CompilerTestSupport.link(temporaryDirectory, mainClass));
    }

    private IrProgram optimized(String mainClass) {
        Program linked = CompilerTestSupport.link(temporaryDirectory, mainClass);
        CompilationPipeline pipeline = new CompilationPipeline();
        return pipeline.optimize(pipeline.lower(linked));
    }

    private static IrMethod method(IrProgram program, String name) {
        return program.methods().stream()
                .filter(candidate -> candidate.reference().name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static long count(IrMethod method, Intrinsic intrinsic) {
        return method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrInstruction.IntrinsicCall call
                        && call.intrinsic() == intrinsic)
                .count();
    }
}
