package io.github.jabrena.juno;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
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
class ExceptionsTest {
    @TempDir
    Path temporaryDirectory;

    private static final String VALIDATION = """
            package demo;
            import io.github.jabrena.juno.api.io.usb.Serial;
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
    void dispatchesAnExplicitThrowToTheFirstMatchingLocalHandler() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Validation", VALIDATION);

        IrMethod check = method(optimized("demo.Validation"), "check");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Validation");

        // Both handlers survive dead-block elimination, so each is reachable from a throw.
        assertThat(count(check, Intrinsic.THROWABLE_GET_MESSAGE)).isEqualTo(2);
        assertThat(count(check, Intrinsic.THROW_DISPATCH)).isEqualTo(2);
        assertThat(count(check, Intrinsic.THROW_RAISE)).isZero();
        // Every throw is caught by a specific clause, so finally's catch-any rethrow path is dead.
        assertThat(result.assembly()).contains("bl juno_throw_dispatch").doesNotContain("bl juno_throw_raise");
        assertThat(result.runtimeShim()).contains(
                "  \"java.lang.IllegalArgumentException\",\n  \"java.lang.IllegalStateException\",\n",
                "extern \"C\" int32_t juno_throw_dispatch(int32_t exception, int32_t caughtMask)",
                "Exception in thread");
    }

    @Test
    void selectsTheHandlerPerThrownClassWithASwitch() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.io.usb.Serial;
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

        assertThat(main.blocks()).anySatisfy(block -> {
            assertThat(block.terminator()).isInstanceOf(IrTerminator.Switch.class);
            assertThat(((IrTerminator.Switch) block.terminator()).keys()).containsExactly(0, 1);
        });
    }

    @Test
    void finallyRunsBeforeAnUncaughtExceptionLeavesTheMethod() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
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

        // The throw reaches finally's catch-any handler, whose rethrow nothing encloses.
        assertThat(count(main, Intrinsic.THROW_DISPATCH)).isEqualTo(1);
        assertThat(count(main, Intrinsic.THROW_RAISE)).isEqualTo(1);
        assertThat(count(main, Intrinsic.SERIAL_PRINTLN_STRING)).isEqualTo(1);
    }

    @Test
    void anInnerFinallyRethrowReachesTheEnclosingCatch() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
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

        // throw -> inner finally handler; its rethrow -> outer catch. Nothing escapes.
        assertThat(count(main, Intrinsic.THROW_DISPATCH)).isEqualTo(2);
        assertThat(count(main, Intrinsic.THROW_RAISE)).isZero();
        assertThat(count(main, Intrinsic.THROWABLE_GET_MESSAGE)).isEqualTo(1);
    }

    @Test
    void aThrowNoLocalHandlerCatchesIsReportedAndPanics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
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
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Uncaught").assembly();

        assertThat(count(main, Intrinsic.THROW_RAISE)).isEqualTo(1);
        assertThat(count(main, Intrinsic.THROW_DISPATCH)).isZero();
        assertThat(count(main, Intrinsic.THROWABLE_GET_MESSAGE)).as("the handler is unreachable").isZero();
        assertThat(assembly).contains("bl juno_throw_raise", "bl juno_throw_check_escape").doesNotContain("bl juno_throw_dispatch");
    }

    @Test
    void programExceptionClassesKeepTheirFieldsAfterTheHeader() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
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

        // Header: class id at #0, message at #4; the program's own `code` field follows at #8.
        assertThat(result.assembly()).contains("str r1, [r0, #0]", "str r1, [r0, #4]", "str r1, [r0, #8]",
                "ldr r1, [r0, #8]", "ldr r0, [r0, #4]");
        assertThat(result.runtimeShim()).contains("  \"demo.Sensors$SensorException\",\n");
    }

    @Test
    void integerDivisionByZeroThrowsACatchableArithmeticException() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.io.usb.Serial;
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
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Division", source);

        IrProgram program = optimized("demo.Division");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Division");

        IrMethod ratio = method(program, "ratio");
        assertThat(newObjects(ratio, "java/lang/ArithmeticException")).isEqualTo(1);
        assertThat(count(ratio, Intrinsic.THROWABLE_GET_MESSAGE)).as("the handler is reachable").isEqualTo(1);
        assertThat(newObjects(method(program, "wideRemainder"), "java/lang/ArithmeticException")).isEqualTo(1);
        // Some handler in the program catches ArithmeticException, so a zero divisor elsewhere (no local handler
        // here, or in main) raises it and unwinds to the caller instead of panicking.
        assertThat(newObjects(method(program, "unguarded"), "java/lang/ArithmeticException")).isEqualTo(1);
        assertThat(count(method(program, "unguarded"), Intrinsic.THROW_RAISE)).isEqualTo(1);
        assertThat(newObjects(method(program, "main"), "java/lang/ArithmeticException")).isEqualTo(1);
        assertThat(result.assembly()).contains(".asciz \"/ by zero\"");
    }

    @Test
    void rejectsExceptionCausesInsteadOfDroppingThem() throws Exception {
        String source = """
                package demo;
                public final class WithCause {
                    public static void main(String[] args) {
                        throw new RuntimeException("wrapped", new IllegalStateException("root"));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WithCause", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WithCause"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("java.lang.RuntimeException.<init>")
                .hasMessageContaining("carry a message only");
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
