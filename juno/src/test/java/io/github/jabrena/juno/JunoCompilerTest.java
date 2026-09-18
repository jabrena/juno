package io.github.jabrena.juno;

import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.linker.Program;
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
    void propagatesLoweredLocalCopiesBeforeFoldingConstantBranches() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class FoldedBranch {
                    static int choose() {
                        int answer = 5;
                        if (answer == 5) return 11;
                        return 22;
                    }
                    public static void main(String[] args) {
                        Delay.millis(choose());
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FoldedBranch", source);
        Program linked = CompilerTestSupport.link(temporaryDirectory, "demo.FoldedBranch");
        CompilationPipeline pipeline = new CompilationPipeline();

        IrProgram optimized = pipeline.optimize(pipeline.lower(linked));

        IrMethod choose = optimized.methods().stream()
                .filter(candidate -> candidate.reference().name().equals("choose"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, choose.blocks().size());
        assertTrue(choose.blocks().stream().noneMatch(
                block -> block.terminator() instanceof IrTerminator.Branch));
    }

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
                import io.github.jabrena.juno.api.led.LedMatrix;
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
                import io.github.jabrena.juno.api.led.LedMatrix;
                import io.github.jabrena.juno.api.led.LedMatrixText;
                public final class Digits {
                    public static void main(String[] args) {
                        int word0 = LedMatrixText.drawDigit(0, 0, 7, 4, 0);
                        LedMatrix.loadFrame(word0, 0, 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits");

        assertTrue(generated.contains("juno_io_github_jabrena_juno_api_led_LedMatrixText_drawDigit"));
        assertTrue(generated.contains("juno_io_github_jabrena_juno_api_led_LedCanvas_setPixel"));
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
    void supportsReferenceArrays() throws Exception {
        String source = """
                package demo;
                public final class Objects {
                    public static void main(String[] args) { Object[] x = new Object[3]; }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Objects", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Objects");

        assertTrue(generated.contains("sizeof(int32_t) * (3)"));
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
        assertEquals(8192, result.report().runtimeRisks().arenaCapacityBytes());
        assertTrue(result.report().runtimeRisks().estimatedMaxStackBytes() > 0);
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
    void supportsDefaultInitializedMutableStaticIntAndFloatFields() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class MutableStatic {
                    static int counter;
                    static float scale;
                    public static void main(String[] args) {
                        counter = counter + 1;
                        scale = 2.5f;
                        Delay.millis((int) (scale * (float) counter));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MutableStatic", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MutableStatic");

        assertTrue(generated.contains("static int32_t juno_field_demo_MutableStatic_counter_"));
        assertTrue(generated.contains("static float juno_field_demo_MutableStatic_scale_"));
        assertTrue(generated.contains("juno_field_demo_MutableStatic_counter_"));
        assertTrue(generated.contains("juno_field_demo_MutableStatic_scale_"));
    }

    @Test
    void executesAReachableStaticFieldInitializerBeforeMain() throws Exception {
        String source = """
                package demo;
                public final class ReadOnlyStatic {
                    static final int NOT_A_CONSTANT_EXPRESSION = compute();
                    static int compute() { return 5; }
                    public static void main(String[] args) {
                        int c = NOT_A_CONSTANT_EXPRESSION;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReadOnlyStatic", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReadOnlyStatic");

        assertTrue(generated.contains("juno_demo_ReadOnlyStatic__clinit_"));
        assertTrue(generated.indexOf("juno_demo_ReadOnlyStatic__clinit_")
                < generated.lastIndexOf("juno_demo_ReadOnlyStatic_main_"));
    }

    @Test
    void supportsALocalArrayWithBoundsCheckedAccessAndAConstantFoldedLength() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Gpio;
                public final class ArrayDemo {
                    static int sum(int[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    public static void main(String[] args) {
                        int[] pins = new int[3];
                        pins[0] = 2;
                        pins[1] = 3;
                        pins[2] = 4;
                        int total = sum(pins, pins.length);
                        Gpio.pinMode(total, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ArrayDemo", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayDemo");

        assertTrue(generated.contains("sizeof(int32_t) * (3)"), "the local array must use arena storage");
        assertTrue(generated.contains(">= 3) juno_panic()"),
                "writes into the 3-element local array must be bounds-checked against its known length");
        assertTrue(generated.contains("(int32_t* arg0, int32_t arg1)"),
                "sum's int[] parameter must be a pointer, with the explicit count as a second parameter");
        // pins.length either folds to a compile-time constant or compileJuno throws (see the negative
        // test below); reaching this point at all already proves it resolved successfully.
    }

    @Test
    void rejectsANonConstantArrayLength() throws Exception {
        String source = """
                package demo;
                public final class NonConstLen {
                    public static void main(String[] args) {
                        int n = 5;
                        int[] arr = new int[n];
                        arr[0] = 1;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NonConstLen", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NonConstLen"));

        assertTrue(exception.getMessage().contains("array length must be a compile-time constant"));
    }

    @Test
    void rejectsLengthOnAnArrayReceivedAsAParameter() throws Exception {
        // .length needs a statically-known size; a parameter's array could have come from any caller
        // with any length, so this is a clear compile error rather than a silently wrong answer.
        String source = """
                package demo;
                public final class LengthOnParam {
                    static int firstLength(int[] values) {
                        return values.length;
                    }
                    public static void main(String[] args) {
                        int[] a = new int[2];
                        firstLength(a);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LengthOnParam", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LengthOnParam"));

        assertTrue(exception.getMessage().contains("demo.LengthOnParam.firstLength"));
        assertTrue(exception.getMessage().contains("array length is not known at compile time"));
    }

    @Test
    void aReassignedArrayLocalDegradesToUncheckedAccessInsteadOfFailing() throws Exception {
        // arr is astore'd twice, so it is not "effectively final" and is not tracked: both stores must
        // still compile (raw pointer semantics), just without a bounds check.
        String source = """
                package demo;
                public final class Reassigned {
                    public static void main(String[] args) {
                        int[] arr = new int[2];
                        arr[0] = 1;
                        arr = new int[3];
                        arr[0] = 2;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Reassigned", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Reassigned");

        assertFalse(generated.contains(">= 2) juno_panic()") || generated.contains(">= 3) juno_panic()"),
                "a reassigned local is not effectively-final and must not be bounds-checked");
        assertTrue(generated.contains("] = v"), "both stores must still compile, as raw pointer writes");
    }

    @Test
    void supportsByteCharAndShortArraysWithTheirNativeStorageWidth() throws Exception {
        String source = """
                package demo;
                public final class ElementTypes {
                    static int sumBytes(byte[] values, int count) {
                        int total = 0;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    public static void main(String[] args) {
                        byte[] buf = new byte[4];
                        buf[0] = 10;
                        char[] chars = new char[3];
                        chars[0] = 'a';
                        short[] shorts = new short[2];
                        shorts[0] = 1000;
                        int total = sumBytes(buf, buf.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ElementTypes", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ElementTypes");

        assertTrue(generated.contains("sizeof(int8_t) * (4)"), "byte[] must be stored as int8_t, not int32_t");
        assertTrue(generated.contains("sizeof(uint16_t) * (3)"), "char[] must be stored as uint16_t");
        assertTrue(generated.contains("sizeof(int16_t) * (2)"), "short[] must be stored as int16_t");
        assertTrue(generated.contains("(int8_t* arg0, int32_t arg1)"),
                "sumBytes's byte[] parameter must be an int8_t pointer");
    }

    @Test
    void supportsFloatArraysAcrossCallsAndForwardedReturns() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class FloatArrays {
                    static float sum(float[] values, int count) {
                        float total = 0.0f;
                        for (int i = 0; i < count; i++) total += values[i];
                        return total;
                    }
                    static float[] identity(float[] values) {
                        return values;
                    }
                    public static void main(String[] args) {
                        float[] samples = new float[3];
                        samples[0] = 1.25f;
                        samples[1] = 2.5f;
                        samples[2] = -0.75f;
                        float[] forwarded = identity(samples);
                        Delay.millis((int) sum(forwarded, samples.length));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatArrays", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatArrays");

        assertTrue(generated.contains("sizeof(float) * (3)"), "float[] must use native float storage");
        assertTrue(generated.contains("(float* arg0, int32_t arg1)"));
        assertTrue(generated.contains("static float* juno_demo_FloatArrays_identity_"));
        assertTrue(generated.contains("return reinterpret_cast<float*>(v"));
        assertTrue(generated.contains(">= 3) juno_panic()"));
    }

    @Test
    void supportsForwardingAReceivedArrayParameterAsAReturnValue() throws Exception {
        String source = """
                package demo;
                public final class ReturnForward {
                    static int[] pick(boolean useA, int[] a, int[] b) {
                        if (useA) {
                            return a;
                        }
                        return b;
                    }
                    public static void main(String[] args) {
                        int[] x = new int[2];
                        int[] y = new int[2];
                        int[] chosen = pick(true, x, y);
                        chosen[1] = 99;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnForward", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnForward");

        assertTrue(generated.contains("static int32_t* juno_demo_ReturnForward_pick_"),
                "an int[]-returning method must have a pointer return type");
        assertTrue(generated.contains("return reinterpret_cast<int32_t*>(v"));
    }

    @Test
    void supportsReturningALocallyAllocatedArenaArray() throws Exception {
        String source = """
                package demo;
                public final class ReturnLocal {
                    static int[] makeArray() {
                        int[] local = new int[3];
                        local[0] = 5;
                        return local;
                    }
                    public static void main(String[] args) {
                        int[] arr = makeArray();
                        arr[0] = 1;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnLocal", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnLocal");

        assertTrue(generated.contains("static int32_t* juno_demo_ReturnLocal_makeArray_"));
        assertTrue(generated.contains("sizeof(int32_t) * (3)"));
    }

    @Test
    void supportsReturningAnArrayAfterABranchMerge() throws Exception {
        String source = """
                package demo;
                public final class ReturnTernary {
                    static int[] pick(boolean useA, int[] a, int[] b) {
                        return useA ? a : b;
                    }
                    public static void main(String[] args) {
                        int[] x = new int[2];
                        int[] y = new int[2];
                        pick(true, x, y);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ReturnTernary", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnTernary");

        assertTrue(generated.contains("static int32_t* juno_demo_ReturnTernary_pick_"));
    }

    @Test
    void supportsLongLocalsWithArithmeticShiftsBitwiseAndComparisons() throws Exception {
        String source = """
                package demo;
                public final class LongMath {
                    public static void main(String[] args) {
                        long acc = 0L;
                        for (long i = 0; i < 10; i++) {
                            acc += i;
                        }
                        long a = 123456789012L;
                        long b = -987654321098L;
                        long sum = a + b;
                        long diff = a - b;
                        long prod = a * 3L;
                        long quot = a / 7L;
                        long rem = a % 7L;
                        long neg = -a;
                        long shiftedLeft = a << 3;
                        long shiftedRight = b >> 2;
                        long shiftedUnsigned = b >>> 2;
                        long anded = a & b;
                        long ored = a | b;
                        long xored = a ^ b;
                        int fromLong = (int) a;
                        long fromInt = fromLong;
                        boolean less = a < b;
                        if (less && sum != diff) {
                            acc = acc + 1;
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongMath", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongMath");

        assertTrue(generated.contains("Closed-world entry point: demo.LongMath.main"));
        assertTrue(generated.contains("juno_ladd("));
        assertTrue(generated.contains("juno_lsub("));
        assertTrue(generated.contains("juno_lmul("));
        assertTrue(generated.contains("juno_ldiv("));
        assertTrue(generated.contains("juno_lrem("));
        assertTrue(generated.contains("juno_lneg("));
        assertTrue(generated.contains("juno_lshl("));
        assertTrue(generated.contains("juno_lshr("));
        assertTrue(generated.contains("juno_lushr("));
        assertTrue(generated.contains("juno_land("));
        assertTrue(generated.contains("juno_lor("));
        assertTrue(generated.contains("juno_lxor("));
        assertTrue(generated.contains("(juno_l > juno_r) - (juno_l < juno_r)"), "lcmp lowers to a plain int result");
    }

    @Test
    void supportsLongMethodParametersReturnsFieldsAndFloatConversions() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class LongParam {
                    static long saved;
                    static long identity(long x) {
                        return x;
                    }
                    public static void main(String[] args) {
                        long r = identity(5L);
                        float asFloat = (float) r;
                        saved = (long) asFloat;
                        Delay.millis((int) saved);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongParam", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongParam");

        assertTrue(generated.contains("static int64_t juno_demo_LongParam_identity_"));
        assertTrue(generated.contains("(int64_t arg0)"));
        assertTrue(generated.contains("locals[0].i32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<uint64_t>(arg0)))"));
        assertTrue(generated.contains("locals[1].i32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<uint64_t>(arg0) >> 32))"));
        assertTrue(generated.contains("static int64_t juno_field_demo_LongParam_saved_"));
        assertTrue(generated.contains("static_cast<float>("));
        assertTrue(generated.contains("juno_f2l("));
    }

    @Test
    void supportsFloatLocalsArithmeticComparisonsConversionsAndLoops() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.Gpio;
                public final class FloatMath {
                    public static void main(String[] args) {
                        float total = 0.0f;
                        for (int i = 0; i < 4; i++) {
                            total += 0.75f;
                        }
                        float adjusted = -(total * 2.0f - 1.0f) / 2.0f;
                        float remainder = adjusted % 1.25f;
                        int narrowed = (int) remainder;
                        float widened = (float) narrowed;
                        float nan = 0.0f / 0.0f;
                        boolean ordered = widened < total && !(nan >= total);
                        Gpio.digitalWrite(13, ordered);
                        Delay.millis(narrowed);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatMath", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMath");

        assertTrue(generated.contains("union JunoSlot"));
        assertTrue(generated.contains("float v"));
        assertTrue(generated.contains("fmodf("));
        assertTrue(generated.contains("juno_f2i("));
        assertTrue(generated.contains("static_cast<float>("));
        assertTrue(generated.contains("isnan("));
    }

    @Test
    void supportsFloatMethodParametersAndReturnValues() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class FloatMethods {
                    static float mix(float left, int scale, float right) {
                        return left * (float) scale + right;
                    }
                    public static void main(String[] args) {
                        float result = mix(1.25f, 2, 0.5f);
                        Delay.millis((int) result);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.FloatMethods", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMethods");

        assertTrue(generated.contains("static float juno_demo_FloatMethods_mix_"));
        assertTrue(generated.contains("(float arg0, int32_t arg1, float arg2)"));
        assertTrue(generated.contains("locals[0].f32 = arg0;"));
        assertTrue(generated.contains("locals[1].i32 = arg1;"));
        assertTrue(generated.contains("locals[2].f32 = arg2;"));
        assertTrue(generated.contains("= juno_demo_FloatMethods_mix_"));
    }

    @Test
    void supportsDoubleLocalsCallsFieldsArraysAndConversions() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class DoubleMath {
                    static double last;
                    static double mix(double left, int scale, double right) {
                        return left * (double) scale + right;
                    }
                    static double[] identity(double[] values) {
                        return values;
                    }
                    public static void main(String[] args) {
                        double[] inputs = new double[2];
                        inputs[0] = 1.25;
                        inputs[1] = 0.5;
                        double[] values = identity(inputs);
                        double value = mix(values[0], 2, values[1]);
                        double remainder = -value % 1.25;
                        double nan = 0.0 / 0.0;
                        float narrowedFloat = (float) remainder;
                        int narrowedInt = (int) remainder;
                        long narrowedLong = (long) remainder;
                        double widenedInt = (double) narrowedInt;
                        double widenedFloat = (double) narrowedFloat;
                        double widenedLong = (double) narrowedLong;
                        last = remainder + widenedInt + widenedFloat + widenedLong;
                        Delay.millis(narrowedInt + (nan < value ? 1 : 0));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.DoubleMath", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DoubleMath");

        assertTrue(generated.contains("static double juno_demo_DoubleMath_mix_"));
        assertTrue(generated.contains("(double arg0, int32_t arg1, double arg2)"));
        assertTrue(generated.contains("locals[0].f64 = arg0;"));
        assertTrue(generated.contains("locals[2].i32 = arg1;"));
        assertTrue(generated.contains("locals[3].f64 = arg2;"));
        assertTrue(generated.contains("sizeof(double) * (2)"));
        assertTrue(generated.contains("static double* juno_demo_DoubleMath_identity_"));
        assertTrue(generated.contains("static double juno_field_demo_DoubleMath_last_"));
        assertTrue(generated.contains("fmod("));
        assertTrue(generated.contains("juno_d2i("));
        assertTrue(generated.contains("juno_d2l("));
    }

    @Test
    void supportsLongArraysAcrossCallsAndForwardedReturns() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class LongArray {
                    static long[] identity(long[] values) {
                        return values;
                    }
                    public static void main(String[] args) {
                        long[] xs = new long[3];
                        xs[0] = 5L;
                        xs[1] = -2L;
                        long value = identity(xs)[0] + xs[1];
                        Delay.millis((int) value);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongArray", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongArray");

        assertTrue(generated.contains("sizeof(int64_t) * (3)"));
        assertTrue(generated.contains("static int64_t* juno_demo_LongArray_identity_"));
        assertTrue(generated.contains("reinterpret_cast<int64_t*>("));
    }

    @Test
    void supportsEnumConstantsAsOrdinalInts() throws Exception {
        // Scoped deliberately to ordinal-int representation: an enum constant is never actually
        // constructed (no heap, no objects), it is just its 0-based declaration-order ordinal, a plain
        // int32_t. getstatic on a recognized enum constant resolves directly to that literal; ==/!=
        // (if_acmpeq/if_acmpne) then works for free, since equal ordinals are equal ints. switch/.name()/
        // .ordinal()/.values()/.valueOf()/per-constant fields and methods are explicitly out of scope.
        String enumSource = """
                package demo;
                public enum Direction {
                    NORTH, SOUTH, EAST, WEST;
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesEnum {
                    static int classify(Direction d) {
                        if (d == Direction.NORTH) {
                            return 0;
                        }
                        return -1;
                    }
                    public static void main(String[] args) {
                        Direction d = Direction.SOUTH;
                        boolean isNorth = d == Direction.NORTH;
                        boolean isSouth = d != Direction.NORTH;
                        int code = classify(Direction.NORTH);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Direction", enumSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesEnum", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnum");

        assertTrue(generated.contains("Closed-world entry point: demo.UsesEnum.main"));
        assertTrue(generated.contains("(int32_t arg0)"),
                "an enum-typed parameter must be a plain int32_t, like every other Juno value");
        // NORTH=0, SOUTH=1: Direction.SOUTH must resolve to the literal 1, Direction.NORTH to 0.
        assertTrue(generated.contains(" = 1;"), "Direction.SOUTH must fold to its ordinal, 1");
        assertTrue(generated.contains(" = 0;"), "Direction.NORTH must fold to its ordinal, 0");
        assertFalse(generated.contains("getstatic"), "getstatic must be resolved away, not passed through");
    }

    @Test
    void supportsSimpleRecordsAsLocalsWithAccessorReads() throws Exception {
        String recordSource = """
                package demo;
                public record Point(int x, int y) {
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesPoint {
                    public static void main(String[] args) {
                        Point p = new Point(3, 4);
                        int total = p.x() + p.y();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesPoint", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesPoint");

        assertTrue(generated.contains("Closed-world entry point: demo.UsesPoint.main"));
        assertTrue(generated.contains("struct JunoObject_demo_Point"));
        assertTrue(generated.contains("field_x_"));
        assertTrue(generated.contains("field_y_"));
        assertTrue(generated.contains("juno_alloc(sizeof(JunoObject_demo_Point)"));
    }

    @Test
    void supportsARecordAsAMethodParameterAndReturnType() throws Exception {
        String recordSource = """
                package demo;
                public record Point(int x, int y) {
                }
                """;
        String usingSource = """
                package demo;
                public final class TakesPoint {
                    static Point echo(Point p) {
                        return p;
                    }
                    static int sum(Point p) {
                        return p.x() + p.y();
                    }
                    public static void main(String[] args) {
                        sum(echo(new Point(1, 2)));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.TakesPoint", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TakesPoint");

        assertTrue(generated.contains("static int32_t juno_demo_TakesPoint_echo_"));
        assertTrue(generated.contains("static int32_t juno_demo_TakesPoint_sum_"));
    }

    @Test
    void supportsConstructingAFinalObjectWithMutableFieldsAndInstanceCalls() throws Exception {
        String source = """
                package demo;
                public final class NewsObject {
                    private int value;
                    NewsObject(int value) { this.value = value; }
                    int add(int amount) { value += amount; return value; }
                    public static void main(String[] args) {
                        NewsObject value = new NewsObject(3);
                        int result = value.add(4);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NewsObject", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NewsObject");

        assertTrue(generated.contains("struct JunoObject_demo_NewsObject"));
        assertTrue(generated.contains("field_value_"));
        assertTrue(generated.contains("int32_t arg_receiver, int32_t arg0"));
    }

    @Test
    void supportsARecordWithACompactConstructor() throws Exception {
        String recordSource = """
                package demo;
                public record Point(int x, int y) {
                    public Point {
                        if (x < 0) x = -x;
                    }
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesCompactPoint {
                    public static void main(String[] args) {
                        Point p = new Point(-3, 4);
                        int x = p.x();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCompactPoint", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCompactPoint");

        assertTrue(generated.contains("juno_ineg("));
    }

    @Test
    void supportsARecordWithACustomAccessorOverride() throws Exception {
        String recordSource = """
                package demo;
                public record Point(int x, int y) {
                    @Override
                    public int x() {
                        return x * 2;
                    }
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesCustomAccessor {
                    public static void main(String[] args) {
                        Point p = new Point(3, 4);
                        int x = p.x();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Point", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesCustomAccessor", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCustomAccessor");

        assertTrue(generated.contains("juno_imul("));
    }

    @Test
    void supportsARecordWithAnArrayComponent() throws Exception {
        String recordSource = """
                package demo;
                public record Labeled(int[] data, int value) {
                }
                """;
        String usingSource = """
                package demo;
                public final class UsesLabeled {
                    public static void main(String[] args) {
                        int[] data = new int[2];
                        Labeled l = new Labeled(data, 3);
                        int v = l.value();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Labeled", recordSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesLabeled", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesLabeled");

        assertTrue(generated.contains("struct JunoObject_demo_Labeled"));
        assertTrue(generated.contains("field_data_"));
    }
}
