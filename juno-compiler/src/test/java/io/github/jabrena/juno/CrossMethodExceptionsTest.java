package io.github.jabrena.juno;

import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
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
@NullMarked
@DisplayName("Exceptions across method boundaries")
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
    @DisplayName("A caller catches what its callee throws")
    void shouldLetACallerCatchWhatItsCalleeThrows() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);
        IrProgram program = optimized("demo.Crossing");
        IrMethod main = method(program, "main");

        // When & Then
        assertThat(new long[] {
                count(main, Intrinsic.THROW_PENDING),
                count(main, Intrinsic.THROW_CATCH),
                count(main, Intrinsic.THROWABLE_GET_MESSAGE)})
                .as("pending poll after middle, catch, get message")
                .containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("A callee without a handler raises and unwinds")
    void shouldRaiseAndUnwindInACalleeWithNoHandler() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);
        IrMethod parse = method(optimized("demo.Crossing"), "parse");

        // When & Then
        assertThat(count(parse, Intrinsic.THROW_RAISE)).as("no handler here: raise and unwind").isEqualTo(1);
    }

    @Test
    @DisplayName("A callee that raises ends with a return that carries no value")
    void shouldEndTheRaisingCalleeWithAReturnWithNoValue() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);
        IrMethod parse = method(optimized("demo.Crossing"), "parse");

        // When & Then
        assertThat(parse.blocks()).anyMatch(block -> block.terminator() instanceof IrTerminator.Return returned
                && returned.value().isEmpty() && endsInRaise(block));
    }

    @Test
    @DisplayName("A middle caller polls after calling the throwing callee and keeps unwinding")
    void shouldPollAfterCallingTheThrowingCalleeAndKeepUnwinding() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);
        IrMethod middle = method(optimized("demo.Crossing"), "middle");

        // When & Then
        assertThat(new long[] {
                count(middle, Intrinsic.THROW_PENDING),
                count(middle, Intrinsic.THROW_CATCH)})
                .as("polls after calling parse, no handler: just keep unwinding")
                .containsExactly(1, 0);
    }

    @Test
    @DisplayName("A small callee that never throws is not polled after the call")
    void shouldNotPollAfterACalleeThatNeverThrows() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Crossing", CALLEE_THROWS);

        // When & Then
        // quiet() is small enough to be inlined into its caller; how it was lowered is what matters here.
        assertThat(count(method(lowered("demo.Crossing"), "quiet"), Intrinsic.THROW_PENDING)).isZero();
    }

    @Test
    @DisplayName("Programs that never throw across a call are not polled")
    void shouldNotPollProgramsThatNeverThrowAcrossACall() throws Exception {
        // Given
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

        // When & Then
        assertThat(result.assembly()).doesNotContain("juno_throw_pending", "juno_throw_check_escape");
    }

    @Test
    @DisplayName("An unmatched exception keeps unwinding past the frame that catches a different class")
    void shouldKeepUnwindingPastAFrameThatCatchesADifferentClass() throws Exception {
        // Given
        IrMethod guarded = method(optimized(compilePartial()), "guarded");

        // When & Then
        // guarded() has a handler, but only for IllegalArgumentException: IllegalStateException funnels into
        // the shared propagation block instead of panicking.
        assertThat(guarded.blocks()).anyMatch(block -> block.terminator() instanceof IrTerminator.Switch switched
                && switched.defaultTarget() != switched.targets().get(0)
                && isReturnBlock(guarded, switched.defaultTarget()));
    }

    @Test
    @DisplayName("The frame with a non-matching handler still has one catch poll")
    void shouldKeepOneCatchPollInTheFrameWithANonMatchingHandler() throws Exception {
        // Given
        IrMethod guarded = method(optimized(compilePartial()), "guarded");

        // When & Then
        assertThat(count(guarded, Intrinsic.THROW_CATCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("A finally block runs when a callee throws and then rethrows")
    void shouldRunFinallyWhenACalleeThrowsAndThenRethrow() throws Exception {
        // Given
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

        // When & Then
        assertThat(new long[] {
                count(work, Intrinsic.THROW_CATCH),
                count(work, Intrinsic.THROW_RAISE),
                count(work, Intrinsic.SERIAL_PRINTLN_STRING)})
                .as("catch-any for fail(), rethrow to the caller, javac inlines finally on both paths")
                .containsExactly(1, 1, 2);
    }

    @Test
    @DisplayName("The caller of the finally-protected method has one catch poll")
    void shouldPollOnceInTheCallerOfTheFinallyProtectedMethod() throws Exception {
        // Given
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

        // When & Then
        assertThat(count(method(optimized("demo.Cleanup"), "main"), Intrinsic.THROW_CATCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("Try-with-resources closes the resource on both paths and rethrows")
    void shouldCloseTheResourceOnBothPathsAndRethrow() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources",
                TRY_WITH_RESOURCES.replace("@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})\n", ""));
        IrProgram program = optimized("demo.Resources");
        IrMethod use = method(program, "use");

        // When
        // close() is called on the normal path and on the exceptional path, where the original exception is
        // then rethrown (addSuppressed is dropped; Juno keeps no suppressed list).
        long closes = use.blocks().stream().flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrInstruction.Call call
                        && call.method().name().equals("close"))
                .count();

        // Then
        assertThat(closes).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Try-with-resources raises on the exceptional path")
    void shouldRaiseOnTheExceptionalPathOfTryWithResources() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources",
                TRY_WITH_RESOURCES.replace("@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})\n", ""));
        IrMethod use = method(optimized("demo.Resources"), "use");

        // When & Then
        assertThat(count(use, Intrinsic.THROW_RAISE)).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Main has one poll per use() call")
    void shouldPollOncePerUseCallInMain() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources",
                TRY_WITH_RESOURCES.replace("@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})\n", ""));

        // When & Then
        assertThat(count(method(optimized("demo.Resources"), "main"), Intrinsic.THROW_CATCH)).isEqualTo(2);
    }

    @Test
    @DisplayName("Generates the same unwinding machinery for both boards")
    void shouldGenerateTheSameUnwindingMachineryForBothBoards() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources", TRY_WITH_RESOURCES);
        List<Path> classpath = CompilerTestSupport.classpath(temporaryDirectory);
        JunoCompiler juno = new JunoCompiler();

        for (Board board : List.of(Board.UNO_R4_WIFI, Board.UNO_Q)) {
            // When
            CompilationResult result = juno.compile(classpath, "demo.Resources", false, Optional.of(board.id()));

            // Then
            assertThat(result.assembly()).as(board.id()).contains("bl juno_throw_pending", "bl juno_throw_catch",
                    "bl juno_throw_raise", "bl juno_throw_check_escape");
        }
    }

    @Test
    @DisplayName("The runtime shim for each board declares the catch and escape-check functions")
    void shouldDeclareTheCatchAndEscapeCheckFunctionsInEachBoardShim() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Resources", TRY_WITH_RESOURCES);
        List<Path> classpath = CompilerTestSupport.classpath(temporaryDirectory);
        JunoCompiler juno = new JunoCompiler();

        for (Board board : List.of(Board.UNO_R4_WIFI, Board.UNO_Q)) {
            // When
            CompilationResult result = juno.compile(classpath, "demo.Resources", false, Optional.of(board.id()));

            // Then
            assertThat(result.runtimeShim()).as(board.id()).contains(
                    "extern \"C\" int32_t juno_throw_catch(int32_t caughtMask)",
                    "extern \"C\" void juno_throw_check_escape()");
        }
    }

    @Test
    @DisplayName("A division by zero in a callee is catchable by its caller")
    void shouldLetACallerCatchADivisionByZeroFromACallee() throws Exception {
        // Given
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

        // When & Then
        assertThat(new long[] {
                count(method(program, "ratio"), Intrinsic.THROW_RAISE),
                count(method(program, "main"), Intrinsic.THROW_CATCH)})
                .as("ratio unwinds on a zero divisor, main catches")
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("A program with no arithmetic handler still panics on division by zero")
    void shouldStillPanicOnDivisionByZeroWithNoArithmeticHandler() throws Exception {
        // Given
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

        // When & Then
        // ratio() is small enough to be inlined into main; how it was lowered is what matters here.
        assertThat(count(method(lowered("demo.Panics"), "ratio"), Intrinsic.THROW_RAISE)).isZero();
    }

    private String compilePartial() throws Exception {
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
        return "demo.Partial";
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
