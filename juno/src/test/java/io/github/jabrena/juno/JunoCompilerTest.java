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
    void reportsAWriteToAMutableStaticFieldAsAnUnsupportedPutstaticOpcode() throws Exception {
        // A non-final (or otherwise non-constant) static field is genuinely unsupported. getstatic itself
        // is now decodable (needed to read an enum constant, see supportsEnumConstantsAsOrdinalInts below),
        // but putstatic never is - there is still no way to write a static field - so a read-modify-write
        // like this fails at the write, not the read. This just confirms the failure is a clear, named
        // CompileException rather than javac's inlining silently making it work too.
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
        assertTrue(exception.getMessage().contains("putstatic"));
    }

    @Test
    void reportsAReadOfANonEnumStaticFieldAsUnsupported() throws Exception {
        // getstatic is decodable now, but only reading an enum constant is actually lowered; reading any
        // other static field (mutable or not) must still fail cleanly, with a message calling out getstatic
        // specifically, not silently misinterpreting the field as some other ordinal.
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

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReadOnlyStatic"));

        assertTrue(exception.getMessage().contains("demo.ReadOnlyStatic.main"));
        assertTrue(exception.getMessage().contains("getstatic"));
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

        assertTrue(generated.contains("int32_t arr_"), "the local array must be declared as a real C array");
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

        assertTrue(generated.contains("int8_t arr_"), "byte[] must be stored as int8_t, not int32_t");
        assertTrue(generated.contains("uint16_t arr_"), "char[] must be stored as uint16_t");
        assertTrue(generated.contains("int16_t arr_"), "short[] must be stored as int16_t");
        assertTrue(generated.contains("(int8_t* arg0, int32_t arg1)"),
                "sumBytes's byte[] parameter must be an int8_t pointer");
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
    void rejectsReturningALocallyAllocatedArray() throws Exception {
        // local's storage is this call's own stack frame; the returned pointer would dangle once
        // makeArray() returns, so this must be a compile error, not silently wrong generated code.
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

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnLocal"));

        assertTrue(exception.getMessage().contains("demo.ReturnLocal.makeArray"));
        assertTrue(exception.getMessage().contains("dangle"));
    }

    @Test
    void rejectsReturningAnArrayThatOnlyExistsAfterABranchMerge() throws Exception {
        // useA ? a : b merges through the operand stack across a block boundary; parameter-forward
        // tracking is deliberately block-local (like the known-length array tracking), so this is a
        // safe, conservative rejection rather than a silently-wrong answer.
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

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnTernary"));

        assertTrue(exception.getMessage().contains("demo.ReturnTernary.pick"));
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
    void rejectsLongAsAMethodParameterOrReturnType() throws Exception {
        // Scoped deliberately to locals + arithmetic only: a long parameter/return would need to
        // renumber every later JVM local slot (long occupies two), which the array-parameter code in
        // particular assumes never happens. Descriptor already rejects 'J' as int-like, so this needs
        // no extra guard - just locking in that the existing validation still covers it.
        String source = """
                package demo;
                public final class LongParam {
                    static long identity(long x) {
                        return x;
                    }
                    public static void main(String[] args) {
                        long r = identity(5L);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongParam", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongParam"));

        assertTrue(exception.getMessage().contains("demo.LongParam.identity"));
        assertTrue(exception.getMessage().contains("int-like"));
    }

    @Test
    void rejectsArraysOfLong() throws Exception {
        // long[] is out of scope for this step; laload/lastore must fail cleanly rather than silently
        // misinterpreting a long array as int32_t-per-element.
        String source = """
                package demo;
                public final class LongArray {
                    public static void main(String[] args) {
                        long[] xs = new long[3];
                        xs[0] = 5L;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.LongArray", source);

        CompileException exception = assertThrows(CompileException.class,
                () -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongArray"));

        assertTrue(exception.getMessage().contains("demo.LongArray.main"));
        assertTrue(exception.getMessage().contains("unsupported opcode"));
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
}
