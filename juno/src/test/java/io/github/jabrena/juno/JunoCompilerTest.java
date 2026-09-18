package io.github.jabrena.juno;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunoCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesReachableMethodsBranchesAndHardwareIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class Main {
                    public static int addTo(int limit) {
                        int value = 0;
                        for (int i = 0; i < limit; i++) value += i;
                        return value;
                    }
                    public static int unused() { return 99; }
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Gpio.digitalWrite(13, addTo(4) == 6);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Main", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Main");

        assertTrue(generated.contains("Closed-world entry point: demo.Main.main"));
        assertTrue(generated.contains("pinMode(call_arg0, call_arg1)"));
        assertTrue(generated.contains("digitalWrite(call_arg0, call_arg1 ? HIGH : LOW)"));
        assertTrue(generated.contains("juno_demo_Main_addTo"));
        assertFalse(generated.contains("juno_demo_Main_unused"));
        assertTrue(generated.contains("goto juno_pc_"));
    }

    @Test
    void erasesDigitalOutputObjectsToPinNumbers() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.DigitalOutput;
                public final class ObjectStyleApi {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(13);
                        led.high();
                        led.low();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ObjectStyleApi", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ObjectStyleApi");

        assertTrue(generated.contains("= juno_digital_output_of(call_arg0);"));
        assertTrue(generated.contains("pinMode(pin, OUTPUT)"));
        assertTrue(generated.contains("digitalWrite(call_receiver, HIGH)"));
        assertTrue(generated.contains("digitalWrite(call_receiver, LOW)"));
        assertFalse(generated.contains("new DigitalOutput"));
    }

    @Test
    void lowersLedMatrixIntrinsicsAndOmitsUnusedHeader() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.LedMatrix;
                public final class Heart {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                        LedMatrix.loadFrame(0x3184a444, 0x44042081, 0x100a0040);
                        LedMatrix.clear();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Heart", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Heart");

        assertTrue(generated.contains("#include \"Arduino_LED_Matrix.h\""));
        assertTrue(generated.contains("ArduinoLEDMatrix juno_led_matrix;"));
        assertTrue(generated.contains("juno_led_matrix_begin()"));
        assertTrue(generated.contains("juno_led_matrix_load_frame(call_arg0, call_arg1, call_arg2)"));
        assertTrue(generated.contains("juno_led_matrix_clear()"));

        String plainSource = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class Plain {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);

        String plainGenerated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        assertFalse(plainGenerated.contains("Arduino_LED_Matrix.h"));
        assertFalse(plainGenerated.contains("ArduinoLEDMatrix"));
    }

    @Test
    void rendersDigitsAndTrimsUnusedLetterGlyphs() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.LedMatrix;
                import io.github.jabrena.juno.api.LedMatrixText;
                public final class Digits {
                    public static void main(String[] args) {
                        int word0 = LedMatrixText.drawDigit(0, 0, 7, 4, 0);
                        LedMatrix.loadFrame(word0, 0, 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits");

        assertTrue(generated.contains("juno_io_github_jabrena_juno_api_LedMatrixText_drawDigit"));
        assertTrue(generated.contains("juno_io_github_jabrena_juno_api_LedCanvas_setPixel"));
        assertFalse(generated.contains("letterARowBits"));
        assertFalse(generated.contains("LedMatrixFont_letterPixel"));
    }

    @Test
    void lowersSerialIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Serial;
                public final class Counter {
                    public static void main(String[] args) {
                        Serial.begin(9600);
                        Serial.print(1);
                        Serial.println(2);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter");

        assertTrue(generated.contains("Serial.begin(static_cast<unsigned long>(call_arg0))"));
        assertTrue(generated.contains("Serial.print(call_arg0)"));
        assertTrue(generated.contains("Serial.println(call_arg0)"));
    }

    @Test
    void lowersMouseIntrinsicsAndOmitsUnusedHeader() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Mouse;
                public final class Wiggle {
                    public static void main(String[] args) {
                        Mouse.begin();
                        Mouse.move(50, -50);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Wiggle", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wiggle");

        assertTrue(generated.contains("#include <Mouse.h>"));
        assertTrue(generated.contains("Mouse.begin()"));
        assertTrue(generated.contains(
                "Mouse.move(static_cast<signed char>(call_arg0), static_cast<signed char>(call_arg1))"));

        String plainSource = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class Plain {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);

        String plainGenerated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        assertFalse(plainGenerated.contains("Mouse.h"));
    }

    @Test
    void reportsUnsupportedBytecodeWithMethodAndOffset() throws Exception {
        String source = """
                package demo;
                public final class Objects {
                    public static void main(String[] args) { new Object(); }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Objects", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Objects"));

        assertTrue(exception.getMessage().contains("demo.Objects.main"));
        assertTrue(exception.getMessage().contains("unsupported opcode"));
        assertTrue(exception.getMessage().contains("bytecode offset"));
    }

    @Test
    void preservesJavaIntegerOverflowUsingUnsignedCppOperations() throws Exception {
        String source = """
                package demo;
                public final class MathProgram {
                    static int calculate(int a, int b) { return -((a + b) * (a - b)); }
                    public static void main() { calculate(Integer.MAX_VALUE, 2); }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathProgram", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MathProgram");

        assertTrue(generated.contains("static_cast<uint32_t>(a) + static_cast<uint32_t>(b)"));
        assertTrue(generated.contains("juno_imul(v"));
        assertTrue(generated.contains("juno_ineg(v"));
    }

    @Test
    void compileWithRequestReturnsAReportAlongsideTheGeneratedSource() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                import io.github.jabrena.juno.api.Delay;
                public final class Reported {
                    static int addTo(int limit) {
                        int value = 0;
                        for (int i = 0; i < limit; i++) value += i;
                        return value;
                    }
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Delay.millis(addTo(4));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Reported", source);

        CompilationResult result = new JunoCompiler().compile(
                new CompilationRequest(List.of(temporaryDirectory, Path.of("target/classes")), "demo.Reported"));

        assertTrue(result.generatedSource().contains("Closed-world entry point: demo.Reported.main"));
        assertEquals("demo.Reported.main([Ljava/lang/String;)V", result.report().entryPoint().displayName());
        assertEquals(2, result.report().reachableMethods(), "main and addTo, both reachable");
        assertTrue(result.report().irBlocks() > 2, "addTo's loop needs more than one block per method");
        assertEquals(Set.of(Intrinsic.GPIO_PIN_MODE, Intrinsic.DELAY_MILLIS), result.report().intrinsics());
    }

    @Test
    void inlinesASameClassStaticFinalIntConstant() throws Exception {
        // static final int fields initialized with a constant expression are compile-time constants
        // per JLS 4.12.4: javac inlines the literal at every use, same class or not, so this never
        // reaches the linker/backend as a getstatic - it's just an iconst/bipush/sipush like any
        // other literal. This mirrors juno-examples' Blink.java (`private static final int LED = 13`),
        // already flashed and verified on hardware; this test just locks the behavior in.
        String source = """
                package demo;
                public final class SameClassConstant {
                    private static final int LIMIT = 4;
                    static int addTo() {
                        int value = 0;
                        for (int i = 0; i < LIMIT; i++) value += i;
                        return value;
                    }
                    public static void main(String[] args) {
                        addTo();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.SameClassConstant", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SameClassConstant");

        assertTrue(generated.contains("Closed-world entry point: demo.SameClassConstant.main"));
        assertFalse(generated.contains("getstatic"), "javac must inline the constant, not emit a field read");
    }

    @Test
    void inlinesACrossClassStaticFinalIntConstant() throws Exception {
        String declaringSource = """
                package demo;
                public final class Limits {
                    static final int MAX = 4;
                }
                """;
        String usingSource = """
                package demo;
                public final class CrossClassConstant {
                    static int addTo() {
                        int value = 0;
                        for (int i = 0; i < Limits.MAX; i++) value += i;
                        return value;
                    }
                    public static void main(String[] args) {
                        addTo();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Limits", declaringSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CrossClassConstant", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CrossClassConstant");

        assertTrue(generated.contains("Closed-world entry point: demo.CrossClassConstant.main"));
        assertFalse(generated.contains("getstatic"));
    }

    @Test
    void reportsAMutableStaticFieldAsAnUnsupportedGetstaticOpcode() throws Exception {
        // A non-final (or otherwise non-constant) static field is genuinely unsupported: it needs
        // getstatic/putstatic, which the decoder rejects. This just confirms the failure is a clear,
        // named CompileException rather than javac's inlining silently making it work too.
        String source = """
                package demo;
                public final class MutableStatic {
                    static int counter = 0;
                    public static void main(String[] args) {
                        counter = counter + 1;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MutableStatic", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MutableStatic"));

        assertTrue(exception.getMessage().contains("demo.MutableStatic.main"));
        assertTrue(exception.getMessage().contains("getstatic"));
    }
}
