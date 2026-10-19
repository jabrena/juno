package io.github.jabrena.juno;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Local {@code try}/{@code catch}/{@code finally}: an explicit {@code throw} reaches a handler in the same
 * method; anything else (no matching handler, or a throw outside any {@code try}) reports the exception
 * and panics.
 */
@DisplayName("Local try, catch and finally lowering")
class ExceptionsTest {
    @TempDir
    Path temporaryDirectory;

    private static final String VALIDATION = """
            package demo;
            import io.github.jabrena.juno.api.io.serial.Serial;
            public final class Validation {
                static int check(int value) {
                    try {
                        if (value < 0) {
                            throw new IllegalArgumentException("negative");
                        }
                        if (value > 100) {
                            throw new IllegalStateException("too large");
                        }
                        return value;
                    } catch (IllegalArgumentException e) {
                        Serial.println(e.getMessage());
                        return 0;
                    } catch (RuntimeException e) {
                        Serial.println(e.getMessage());
                        return 100;
                    } finally {
                        Serial.println("checked");
                    }
                }
                public static void main(String[] args) {
                    Serial.println(check(-5) + check(500));
                }
            }
            """;

    @Test
    @DisplayName("Dispatches an explicit throw to its matching local handlers")
    void shouldDispatchAnExplicitThrowToItsMatchingLocalHandlers() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Validation", VALIDATION);
        IrMethod check = method(optimized("demo.Validation"), "check");

        // When & Then
        // Both handlers survive dead-block elimination, so each is reachable from a throw.
        assertThat(new long[] {
                count(check, Intrinsic.THROWABLE_GET_MESSAGE),
                count(check, Intrinsic.THROW_DISPATCH),
                count(check, Intrinsic.THROW_RAISE)})
                .as("get message, dispatch, raise")
                .containsExactly(2, 2, 0);
    }

    @Test
    @DisplayName("Every throw is caught by a specific clause, so no rethrow is emitted in the assembly")
    void shouldNotRaiseInTheAssemblyWhenEveryThrowIsCaught() throws Exception {
        // Given
        CompilationResult result = compileValidation();

        // When & Then
        // Every throw is caught by a specific clause, so finally's catch-any rethrow path is dead.
        assertThat(result.assembly()).contains("bl juno_throw_dispatch").doesNotContain("bl juno_throw_raise");
    }

    @Test
    @DisplayName("The runtime shim carries the handler class table and the dispatch function")
    void shouldCarryTheHandlerClassTableAndTheDispatchFunctionInTheShim() throws Exception {
        // Given
        CompilationResult result = compileValidation();

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "  \"java.lang.IllegalArgumentException\",\n  \"java.lang.IllegalStateException\",\n",
                "extern \"C\" int32_t juno_throw_dispatch(int32_t exception, int32_t caughtMask)",
                "Exception in thread");
    }

    @Test
    @DisplayName("Selects the handler per thrown class with a switch")
    void shouldSelectTheHandlerPerThrownClassWithASwitch() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.time.Clock;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Selector {
                    public static void main(String[] args) {
                        RuntimeException picked = Clock.millis() == 0
                                ? new IllegalArgumentException("a")
                                : new IllegalStateException("b");
                        try {
                            throw picked;
                        } catch (IllegalArgumentException e) {
                            Serial.println("argument");
                        } catch (IllegalStateException e) {
                            Serial.println("state");
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Selector", source);
        IrMethod main = method(optimized("demo.Selector"), "main");

        // When & Then
        assertThat(main.blocks()).anySatisfy(block -> {
            assertThat(block.terminator()).isInstanceOf(IrTerminator.Switch.class);
            assertThat(((IrTerminator.Switch) block.terminator()).keys()).containsExactly(0, 1);
        });
    }

    @Test
    @DisplayName("Finally runs before an uncaught exception leaves the method")
    void shouldRunFinallyBeforeAnUncaughtExceptionLeavesTheMethod() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Cleanup {
                    public static void main(String[] args) {
                        try {
                            throw new IllegalStateException("boom");
                        } finally {
                            Serial.println("cleanup");
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Cleanup", source);
        IrMethod main = method(optimized("demo.Cleanup"), "main");

        // When & Then
        // The throw reaches finally's catch-any handler, whose rethrow nothing encloses.
        assertThat(new long[] {
                count(main, Intrinsic.THROW_DISPATCH),
                count(main, Intrinsic.THROW_RAISE),
                count(main, Intrinsic.SERIAL_PRINTLN_STRING)})
                .as("dispatch, raise, println")
                .containsExactly(1, 1, 1);
    }

    @Test
    @DisplayName("An inner finally rethrow reaches the enclosing catch")
    void shouldReachTheEnclosingCatchFromAnInnerFinallyRethrow() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Nested {
                    public static void main(String[] args) {
                        try {
                            try {
                                throw new UnsupportedOperationException("not yet");
                            } finally {
                                Serial.println("inner finally");
                            }
                        } catch (UnsupportedOperationException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Nested", source);
        IrMethod main = method(optimized("demo.Nested"), "main");

        // When & Then
        // throw -> inner finally handler; its rethrow -> outer catch. Nothing escapes.
        assertThat(new long[] {
                count(main, Intrinsic.THROW_DISPATCH),
                count(main, Intrinsic.THROW_RAISE),
                count(main, Intrinsic.THROWABLE_GET_MESSAGE)})
                .as("dispatch, raise, get message")
                .containsExactly(2, 0, 1);
    }

    @Test
    @DisplayName("A throw with no local handler is reported and panics")
    void shouldReportAUncaughtThrowAndPanic() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Uncaught {
                    public static void main(String[] args) {
                        try {
                            throw new IllegalStateException("boom");
                        } catch (IllegalArgumentException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Uncaught", source);
        IrMethod main = method(optimized("demo.Uncaught"), "main");

        // When & Then
        assertThat(new long[] {
                count(main, Intrinsic.THROW_RAISE),
                count(main, Intrinsic.THROW_DISPATCH),
                count(main, Intrinsic.THROWABLE_GET_MESSAGE)})
                .as("raise, dispatch, get message: the handler is unreachable")
                .containsExactly(1, 0, 0);
    }

    @Test
    @DisplayName("An uncaught throw raises in the assembly without dispatching")
    void shouldRaiseInTheAssemblyWithoutDispatchingForAnUncaughtThrow() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Uncaught {
                    public static void main(String[] args) {
                        try {
                            throw new IllegalStateException("boom");
                        } catch (IllegalArgumentException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Uncaught", source);
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Uncaught").assembly();

        // When & Then
        assertThat(assembly).contains("bl juno_throw_raise", "bl juno_throw_check_escape")
                .doesNotContain("bl juno_throw_dispatch");
    }

    @Test
    @DisplayName("Program exception classes keep their fields after the header")
    void shouldKeepProgramExceptionFieldsAfterTheHeader() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Sensors {
                    static final class SensorException extends RuntimeException {
                        final int code;
                        SensorException(String message, int code) {
                            super(message);
                            this.code = code;
                        }
                    }
                    public static void main(String[] args) {
                        try {
                            throw new SensorException("offline", 7);
                        } catch (RuntimeException e) {
                            Serial.println(e.getMessage());
                        }
                        try {
                            throw new SensorException("stale", 3);
                        } catch (SensorException e) {
                            Serial.println(e.code);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Sensors", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Sensors");

        // When & Then
        // Header: class id at #0, message at #4; the program's own `code` field follows at #8.
        assertThat(result.assembly()).contains("str r1, [r0, #0]", "str r1, [r0, #4]", "str r1, [r0, #8]",
                "ldr r1, [r0, #8]", "ldr r0, [r0, #4]");
    }

    @Test
    @DisplayName("The runtime shim names the program exception class")
    void shouldNameTheProgramExceptionClassInTheShim() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Sensors {
                    static final class SensorException extends RuntimeException {
                        final int code;
                        SensorException(String message, int code) {
                            super(message);
                            this.code = code;
                        }
                    }
                    public static void main(String[] args) {
                        try {
                            throw new SensorException("offline", 7);
                        } catch (RuntimeException e) {
                            Serial.println(e.getMessage());
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Sensors", source);

        // When & Then
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Sensors").runtimeShim())
                .contains("  \"demo.Sensors$SensorException\",\n");
    }

    @Test
    @DisplayName("Integer division by zero throws a catchable ArithmeticException")
    void shouldThrowACatchableArithmeticExceptionForIntegerDivisionByZero() throws Exception {
        // Given
        IrProgram program = divisionProgram();
        IrMethod ratio = method(program, "ratio");

        // When & Then
        assertThat(new long[] {
                newObjects(ratio, "java/lang/ArithmeticException"),
                count(ratio, Intrinsic.THROWABLE_GET_MESSAGE)})
                .as("allocations, get message: the handler is reachable")
                .containsExactly(1, 1);
    }

    @Test
    @DisplayName("Long remainder by zero throws an ArithmeticException")
    void shouldThrowAnArithmeticExceptionForLongRemainderByZero() throws Exception {
        // Given
        IrProgram program = divisionProgram();

        // When & Then
        assertThat(newObjects(method(program, "wideRemainder"), "java/lang/ArithmeticException")).isEqualTo(1);
    }

    @Test
    @DisplayName("A zero divisor with no local handler unwinds to the caller instead of panicking")
    void shouldUnwindToTheCallerForAZeroDivisorWithNoLocalHandler() throws Exception {
        // Given
        IrProgram program = divisionProgram();

        // When & Then
        // Some handler in the program catches ArithmeticException, so a zero divisor elsewhere (no local handler
        // here, or in main) raises it and unwinds to the caller instead of panicking.
        assertThat(new long[] {
                newObjects(method(program, "unguarded"), "java/lang/ArithmeticException"),
                count(method(program, "unguarded"), Intrinsic.THROW_RAISE)})
                .as("allocations, raise")
                .containsExactly(1, 1);
    }

    @Test
    @DisplayName("Main allocates an ArithmeticException for an unhandled zero divisor")
    void shouldAllocateAnArithmeticExceptionInMainForAnUnhandledZeroDivisor() throws Exception {
        // Given
        IrProgram program = divisionProgram();

        // When & Then
        assertThat(newObjects(method(program, "main"), "java/lang/ArithmeticException")).isEqualTo(1);
    }

    @Test
    @DisplayName("The assembly carries the divide-by-zero message")
    void shouldCarryTheDivideByZeroMessageInTheAssembly() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Division", divisionSource());
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Division");

        // When & Then
        assertThat(result.assembly()).contains(".asciz \"/ by zero\"");
    }

    @Test
    @DisplayName("Rejects exception causes instead of dropping them")
    void shouldRejectExceptionCausesInsteadOfDroppingThem() throws Exception {
        // Given
        String source = """
                package demo;
                public final class WithCause {
                    public static void main(String[] args) {
                        throw new RuntimeException("wrapped", new IllegalStateException("root"));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WithCause", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WithCause"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("java.lang.RuntimeException.<init>")
                .hasMessageContaining("carry a message only");
    }

    private CompilationResult compileValidation() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Validation", VALIDATION);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Validation");
    }

    private IrProgram divisionProgram() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Division", divisionSource());
        return optimized("demo.Division");
    }

    private static String divisionSource() {
        return """
                package demo;
                import io.github.jabrena.juno.api.time.Clock;
                import io.github.jabrena.juno.api.io.serial.Serial;
                public final class Division {
                    static int ratio(int total, int count) {
                        try {
                            return total / count;
                        } catch (ArithmeticException e) {
                            Serial.println(e.getMessage());
                            return 0;
                        }
                    }
                    static long wideRemainder(long total, long count) {
                        try {
                            return total % count;
                        } catch (RuntimeException e) {
                            return -1L;
                        }
                    }
                    static int unguarded(int total, int count) {
                        try {
                            return total / count;
                        } catch (IllegalStateException e) {
                            return -1;
                        }
                    }
                    public static void main(String[] args) {
                        Serial.println(ratio(10, 0));
                        Serial.println(wideRemainder(10L, 0L));
                        Serial.println(unguarded(10, 2) + 10 / Clock.millis());
                    }
                }
                """;
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

    private static long newObjects(IrMethod method, String className) {
        return method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrInstruction.NewObject object
                        && object.className().equals(className))
                .count();
    }

    private static long count(IrMethod method, Intrinsic intrinsic) {
        return method.blocks().stream()
                .flatMap(block -> block.instructions().stream())
                .filter(instruction -> instruction instanceof IrInstruction.IntrinsicCall call
                        && call.intrinsic() == intrinsic)
                .count();
    }
}
