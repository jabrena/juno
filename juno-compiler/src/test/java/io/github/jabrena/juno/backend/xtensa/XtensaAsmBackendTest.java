package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Xtensa assembly backend")
class XtensaAsmBackendTest {
    private static final String NANO_ESP32 = Board.NANO_ESP32.id();

    private static final String BLINK = """
                    DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
                    led.high();
                    Delay.millis(500);
                    led.low();
                    Delay.micros(10);
            """;
    private static final String BLINK_IMPORTS = """
            import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
            import io.github.jabrena.juno.util.Delay;
            """;
    private static final String SERIAL = """
                    Serial.begin(BaudRate.BAUD_115200);
                    int counter = 0;
                    for (int index = 0; index < 3; index++) {
                        Serial.print("count ");
                        Serial.println(counter);
                        counter = counter + 1;
                    }
            """;
    private static final String SERIAL_IMPORTS = """
            import io.github.jabrena.juno.hardware.serial.BaudRate;
            import io.github.jabrena.juno.hardware.serial.Serial;
            """;

    private static final String LONGS = """
                    long value = MCU.getGpio().analogRead(0);
                    long other = MCU.getGpio().analogRead(1) + 1L;
                    int shift = MCU.getGpio().analogRead(2);
                    long mixed = value * value + other - value / other + value % other;
                    mixed = (mixed & other) | (mixed ^ value);
                    mixed = (mixed << shift) + (mixed >> shift) + (mixed >>> shift) - mixed;
                    if (mixed > other) {
                        mixed = -mixed;
                    }
                    Serial.println((int) (mixed + 5_000_000_000L));
            """;
    private static final String FLOATS = """
                    int raw = MCU.getGpio().analogRead(3);
                    float reading = raw;
                    float volts = reading * 1.5f + 0.25f - reading / 3f + reading % 2f;
                    if (volts > 2.0f) {
                        volts = -volts;
                    }
                    long ticks = (long) volts;
                    float back = ticks;
                    Serial.println((int) (volts + back));
            """;

    private static final String DOUBLES = """
                    double sample = MCU.getGpio().analogRead(4);
                    double mixed = sample * 0.1 + 2.5 - sample / 3.0 + sample % 2.0;
                    if (mixed > 2.0) {
                        mixed = -mixed;
                    }
                    long ticks = (long) mixed;
                    double back = ticks;
                    float narrow = (float) mixed;
                    double wide = narrow;
                    Serial.println((int) (mixed + back + wide));
            """;

    private static final String LAMBDAS = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.MCU;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32.class)
            public final class NanoLambdas {
                interface Operation {
                    int apply(int value);
                }
                interface Factory {
                    Box create(int value);
                }
                static final class Box {
                    private final int value;
                    Box(int value) {
                        this.value = value;
                    }
                    int plus(int amount) {
                        return value + amount;
                    }
                }
                static final class Doubler implements Operation {
                    public int apply(int value) {
                        return value * 2;
                    }
                }
                public static void main(String[] args) {
                    int raw = MCU.getGpio().analogRead(0);
                    int offset = raw & 7;
                    Operation square = value -> value * value;
                    Operation captured = value -> value + offset + raw;
                    Box box = new Box(raw);
                    Operation bound = box::plus;
                    Factory factory = Box::new;
                    Operation[] mixed = {new Doubler(), square, captured};
                    Serial.println(square.apply(raw) + captured.apply(1) + bound.apply(2)
                            + factory.create(raw).plus(3) + mixed[raw % 3].apply(4));
                }
            }
            """;

    private static final String THREADS = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.MCU;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32.class)
            public final class NanoThreads {
                static final class Total {
                    int value;
                }
                static final class Counter implements Runnable {
                    private final Total total;
                    private final int limit;
                    Counter(Total total, int limit) {
                        this.total = total;
                        this.limit = limit;
                    }
                    public void run() {
                        for (int index = 0; index < limit; index++) {
                            synchronized (total) {
                                total.value = total.value + 1;
                            }
                        }
                    }
                }
                public static void main(String[] args) throws InterruptedException {
                    int limit = MCU.getGpio().analogRead(0);
                    Total total = new Total();
                    Thread first = Thread.startVirtualThread(new Counter(total, limit));
                    Thread second = Thread.startVirtualThread(new Counter(total, limit));
                    first.join();
                    second.join();
                    Serial.println(total.value);
                }
            }
            """;

    private static final String SHAPES = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.MCU;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32.class)
            public final class NanoShapes {
                interface Shape {
                    int area();
                }
                static final class Square implements Shape {
                    private final int side;
                    Square(int side) {
                        this.side = side;
                    }
                    public int area() {
                        return side * side;
                    }
                }
                static final class Strip implements Shape {
                    private final int length;
                    Strip(int length) {
                        this.length = length;
                    }
                    public int area() {
                        return length;
                    }
                }
                static int total;
                static Shape pick(int value) {
                    return value > 3 ? new Square(value) : new Strip(value);
                }
                static int classify(int value) {
                    switch (value) {
                        case 1: return 10;
                        case 2: return 20;
                        case 7: return 70;
                        default: return 0;
                    }
                }
                public static void main(String[] args) {
                    int value = MCU.getGpio().analogRead(0);
                    byte[] bytes = new byte[4];
                    char[] chars = new char[4];
                    short[] shorts = new short[4];
                    int[][] grid = new int[2][3];
                    bytes[value & 3] = (byte) value;
                    chars[value & 3] = (char) value;
                    shorts[value & 3] = (short) value;
                    grid[value & 1][value % 3] = value | 5;
                    total = bytes[1] + chars[2] + shorts[3] + grid[1][2] + (value ^ 9) + bytes.length;
                    Shape shape = pick(value);
                    total = total + shape.area() + classify(value);
                    if (value > 2) {
                        total = total + 1;
                    }
                    if (value <= 8) {
                        total = total - 1;
                    }
                    if (value >= 4) {
                        total = total * 2;
                    }
                    Serial.println(total);
                }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    private CompilationResult compile(String className, String body, String imports) throws Exception {
        return compileSource(className, """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                %s
                @Board(ArduinoNanoEsp32.class)
                public final class %s {
                    public static void main(String[] args) {
                %s
                    }
                }
                """.formatted(imports, className.substring(className.lastIndexOf('.') + 1), body));
    }

    private CompilationResult compileSource(String className, String source) throws Exception {
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        return new JunoCompiler().compile(CompilerTestSupport.classpath(classes), className, false,
                Optional.of(NANO_ESP32));
    }

    @Test
    @DisplayName("Resolves the Nano ESP32 to the Xtensa backend under the ESP32 core")
    void shouldResolveTheNanoEsp32ToTheXtensaBackend() {
        // Given
        Board board = Board.fromId(NANO_ESP32);

        // When & Then
        assertThat(new String[] {board.name(), board.fqbn(), board.core().architecture().name()})
                .containsExactly("NANO_ESP32", "arduino:esp32:nano_nora", Architecture.XTENSA.name());
    }

    @Test
    @DisplayName("The Nano ESP32 resolves from its API class name and annotation argument")
    void shouldResolveTheNanoEsp32FromItsApiClassName() {
        // When & Then
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoNanoEsp32"))
                .isEqualTo(Board.NANO_ESP32);
        assertThat(Board.NANO_ESP32.annotationArgument()).isEqualTo("ArduinoNanoEsp32.class");
    }

    @Test
    @DisplayName("Rejects a non-Xtensa board for the Xtensa backend")
    void shouldRejectANonXtensaBoard() {
        // When & Then
        assertThatThrownBy(() -> new XtensaAsmBackend(false, Board.UNO_R4_WIFI, RuntimeConfig.DEFAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an Xtensa board");
    }

    @Test
    @DisplayName("Blink opens a windowed frame and calls the GPIO wrappers and delays through call8")
    void shouldLowerBlinkToWindowedCalls() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoBlink", BLINK, BLINK_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains(".global juno_NanoBlink_asm", "entry a1, ", "movi a8, 13\n", "movi a11, 1\n")
                .contains("call8 juno_pin_mode\n", "call8 juno_digital_write\n", "call8 delay\n",
                        "call8 delayMicroseconds\n", "retw\n")
                .doesNotContain("call8 pinMode\n", "call8 digitalWrite\n");
    }

    @Test
    @DisplayName("The shim maps printed pin numbers through the core's remap macros")
    void shouldDefineThePinRemapWrappersInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoBlink", BLINK, BLINK_IMPORTS);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("extern \"C\" void juno_pin_mode(int32_t pin, int32_t mode) {\n  pinMode(pin, mode);")
                .contains("extern \"C\" void juno_digital_write(int32_t pin, int32_t value) {")
                .doesNotContain("extern \"C\" void yield()", "Arduino_LED_Matrix");
    }

    @Test
    @DisplayName("Records the caller's stack pointer as the collector's stack top before zeroing below it")
    void shouldRecordTheStackTopAboveTheEntryFrame() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoBlink", BLINK, BLINK_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("movi a8, juno_gc_stack_top\n", "addi a9, a1, -16\n", ".LjunoZeroStack");
    }

    @Test
    @DisplayName("Serial output passes the baud rate, string and counter in a10")
    void shouldLowerSerialOutput() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoSerial", SERIAL, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("call8 juno_serial_begin\n", "call8 juno_serial_print_str\n", "call8 juno_serial_println\n")
                .contains("movi a10, juno_str0\n", "call8 yield\n");
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_serial_println(int32_t value)");
    }

    @Test
    @DisplayName("Static calls pass arguments in a10-a15 and the callee spills them from a2-a7")
    void shouldPassArgumentsThroughTheRegisterWindow() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoNanoEsp32.class)
                public final class NanoCalls {
                    static int mix(int a, int b, int c) {
                        return a <= 0 ? b - c : mix(a - 1, a * b, c);
                    }
                    public static void main(String[] args) {
                        Serial.println(mix(MCU.getGpio().analogRead(0), 7, 2));
                    }
                }
                """;
        CompilationResult result = compileSource("demo.NanoCalls", source);

        // When & Then
        assertThat(result.assembly())
                .contains("s32i a2, a1, ", "s32i a3, a1, ", "s32i a4, a1, ")
                .contains("mull a8, a8, a9\n", "sub a8, a8, a9\n")
                .containsPattern("call8 juno_fn\\d+\n    s32i a10, a1, ");
    }

    @Test
    @DisplayName("Integer division gives MIN_VALUE / -1 Java's wrapped quotient")
    void shouldGuardDivisionByMinusOne() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoDivide", """
                        int value = MCU.getGpio().analogRead(0);
                        int divisor = MCU.getGpio().analogRead(1);
                        Serial.println(value / divisor);
                        Serial.println(value % divisor);
                        Serial.println(value >> divisor);
                        Serial.println(value >>> divisor);
                        Serial.println(value << divisor);
                """, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("quos a8, a8, a9\n", "rems a8, a8, a9\n", "neg a8, a8\n", "movi a10, -1\n")
                .contains("ssr a9\n    sra a8, a8\n", "ssr a9\n    srl a8, a8\n", "ssl a9\n    sll a8, a8\n");
    }

    @Test
    @DisplayName("The Nano ESP32 is no UNO generation, so every BoardModel query folds to false")
    void shouldFoldEveryBoardModelQueryToFalse() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoModel", """
                        Serial.println(MCU.getBoardModel().isR4() ? 1 : 0);
                """, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly()).contains("_asmblock0:\n    movi a8, 0\n");
    }

    @Test
    @DisplayName("Rejects networking, which the Nano ESP32 has no capability for yet")
    void shouldRejectNetworking() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.NanoWifi", "Wifi.status();",
                "import io.github.jabrena.juno.hardware.net.Wifi;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Nano ESP32 has no onboard WiFi module");
    }

    @Test
    @DisplayName("Lowers long arithmetic, shifts and comparisons to the shim's juno_l* helpers in register pairs")
    void shouldLowerLongArithmetic() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoLong", LONGS, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("call8 juno_lmul\n", "call8 juno_ladd\n", "call8 juno_lsub\n", "call8 juno_ldiv\n")
                .contains("call8 juno_lrem\n", "call8 juno_land\n", "call8 juno_lor\n", "call8 juno_lxor\n")
                .contains("call8 juno_lshl\n", "call8 juno_lshr\n", "call8 juno_lushr\n", "call8 juno_lneg\n")
                .contains("call8 juno_lcmp\n", "srai a8, a8, 31\n")
                .containsPattern("s32i a10, a1, \\d+\n    s32i a11, a1, \\d+\n");
        assertThat(result.runtimeShim()).contains("extern \"C\" int64_t juno_ladd(int64_t a, int64_t b)");
    }

    @Test
    @DisplayName("Lowers float arithmetic, comparisons and conversions to the shim's juno_f* helpers")
    void shouldLowerFloatArithmetic() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoFloat", FLOATS, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("call8 juno_i2f\n", "call8 juno_fmul\n", "call8 juno_fadd\n", "call8 juno_fsub\n")
                .contains("call8 juno_fdiv\n", "call8 juno_frem\n", "call8 juno_f2i\n", "call8 juno_fcmp\n")
                .contains("call8 juno_l2f\n", "call8 juno_f2l\n")
                .contains("movi a8, " + Float.floatToRawIntBits(1.5f) + "\n")
                .contains("movi a9, " + Integer.MIN_VALUE + "\n    xor a8, a8, a9\n");
        assertThat(result.runtimeShim())
                .contains("#include <math.h>", "extern \"C\" JUNO_ASM_ABI float juno_fadd(float a, float b)");
    }

    @Test
    @DisplayName("Passes and returns long and float values across generated methods")
    void shouldPassWideValuesBetweenMethods() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoWideCalls", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoNanoEsp32.class)
                public final class NanoWideCalls {
                    static long total;
                    static float scale = 0.5f;
                    static long twice(int pad, long value) {
                        return value + value + pad;
                    }
                    static float scaled(float value) {
                        return value * scale;
                    }
                    public static void main(String[] args) {
                        int raw = MCU.getGpio().analogRead(0);
                        total = twice(raw, raw * 1000L);
                        Serial.println((int) (total >>> 3));
                        Serial.println((int) scaled(raw));
                    }
                }
                """);

        // When & Then
        assertThat(result.assembly())
                .contains("call8 juno_ladd\n", "call8 juno_fmul\n")
                .containsPattern("call8 juno_fn\\d+\n    s32i a10, a1, \\d+\n    s32i a11, a1, ");
    }

    @Test
    @DisplayName("Lowers double arithmetic, comparisons and conversions to the shim's juno_d* helpers in register pairs")
    void shouldLowerDoubleArithmetic() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoDouble", DOUBLES, SERIAL_IMPORTS);

        // When & Then
        long bits = Double.doubleToRawLongBits(0.1);
        assertThat(result.assembly())
                .contains("call8 juno_i2d\n", "call8 juno_dmul\n", "call8 juno_dadd\n", "call8 juno_dsub\n")
                .contains("call8 juno_ddiv\n", "call8 juno_drem\n", "call8 juno_dcmp\n", "call8 juno_d2i\n")
                .contains("call8 juno_l2d\n", "call8 juno_d2l\n", "call8 juno_f2d\n", "call8 juno_d2f\n")
                .contains("movi a8, " + (int) bits + "\n    movi a9, " + (int) (bits >>> 32) + "\n")
                .contains("movi a10, " + Integer.MIN_VALUE + "\n    xor a9, a9, a10\n");
        assertThat(result.runtimeShim()).contains("extern \"C\" JUNO_ASM_ABI double juno_dadd(double a, double b)");
    }

    @Test
    @DisplayName("Allows the double-precision Math functions, which the ESP32 toolchain's libm provides")
    void shouldLowerDoublePrecisionMath() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoMath", """
                        double angle = MCU.getGpio().analogRead(0) / 100.0;
                        Serial.println(Math.sin(angle) + Math.sqrt(angle) + Math.pow(angle, 3) + Math.atan2(angle, 2));
                """, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.runtimeShim()).contains("sin(", "sqrt(", "pow(", "atan2(");
        assertThat(result.assembly()).containsPattern("call8 juno_math_\\w+\n");
    }

    @Test
    @DisplayName("Double programs and Math functions assemble with the ESP32-S3 toolchain")
    void shouldAssembleDoubleArithmeticWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compile("demo.NanoDoubleSmoke", DOUBLES + """
                        Serial.println(Math.sin(sample) + Math.pow(sample, 0.5));
                """, SERIAL_IMPORTS);

        // When & Then
        assemble(gcc, "NanoDoubleSmoke", result);
    }

    @Test
    @DisplayName("Long and float programs assemble with the ESP32-S3 toolchain")
    void shouldAssembleWideArithmeticWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compile("demo.NanoWideSmoke", LONGS + FLOATS,
                SERIAL_IMPORTS);

        // When & Then
        assemble(gcc, "NanoWideSmoke", result);
    }

    @Test
    @DisplayName("A GPIO, delay and serial program assembles with the ESP32-S3 toolchain")
    void shouldAssembleWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compile("demo.NanoSmoke", BLINK + SERIAL + """
                        int[] values = new int[4];
                        values[counter & 3] = counter / 3;
                        Serial.println(values.length + values[1]);
                """, BLINK_IMPORTS + SERIAL_IMPORTS);

        // When & Then
        assemble(gcc, "NanoSmoke", result);
    }

    @Test
    @DisplayName("Fields, narrow arrays, switches, comparisons and interface dispatch assemble with the toolchain")
    void shouldAssembleTheWiderInstructionSet() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compileSource("demo.NanoShapes", SHAPES);

        // When & Then
        assemble(gcc, "NanoShapes", result);
    }

    @Test
    @DisplayName("Lowers fields, narrow arrays, switches, every comparison and interface dispatch")
    void shouldLowerTheWiderInstructionSet() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoShapes", SHAPES);

        // When & Then
        assertThat(result.assembly())
                .contains("l8ui a9, a8, 0\n    sext a9, a9, 7\n", "l16ui a9, a8, 0\n", "l16si a9, a8, 0\n")
                .contains("s8i a9, a8, 0\n", "s16i a9, a8, 0\n", "addx2 a8, a9, a8\n")
                .contains("and a8, a8, a9\n", "or a8, a8, a9\n", "xor a8, a8, a9\n")
                .contains("sext a8, a8, 15\n", "extui a8, a8, 0, 16\n", "bltu a8, a9, .LboundsOk")
                .contains("bge a9, a8, ", "blt a9, a8, ", "call8 juno_panic\n")
                .containsPattern("bne a8, a9, \\.Lswitchnext\\d+\n");
    }

    @Test
    @DisplayName("Lowers non-capturing and capturing lambdas, method references and lambda interface targets")
    void shouldLowerLambdas() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoLambdas", LAMBDAS);

        // When & Then
        assertThat(result.assembly())
                .contains("juno_lambda_0:\n    .long ", "movi a8, juno_lambda_0\n")
                .contains("call8 juno_alloc\n")
                .containsPattern("s32i a9, a8, 4\n")
                .containsPattern("l32i a8, a1, \\d+\n    l32i a10, a8, 4\n")
                .containsPattern("call8 juno_fn\\d+\n");
    }

    @Test
    @DisplayName("Lambda programs assemble with the ESP32-S3 toolchain")
    void shouldAssembleLambdasWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compileSource("demo.NanoLambdas", LAMBDAS);

        // When & Then
        assemble(gcc, "NanoLambdas", result);
    }

    @Test
    @DisplayName("Threads get safepoints on method entry and backedges, a global entry symbol and a main-exit join")
    void shouldLowerThreadsWithSafepoints() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoThreads", THREADS);

        // When & Then
        assertThat(result.assembly())
                .contains("    .global juno_thread_entry\n", "juno_thread_entry:\n")
                .contains("call8 juno_thread_start_virtual\n", "call8 juno_thread_join\n")
                .contains("call8 juno_monitor_enter\n", "call8 juno_monitor_exit\n")
                .contains("call8 juno_thread_backedge\n", "call8 juno_thread_main_exit\n")
                .containsPattern("entry a1, \\d+\n(    s32i a\\d, a1, \\d+\n)*    call8 juno_thread_safepoint\n")
                .doesNotContain("call8 yield\n", "juno_java_code_start");
        assertThat(result.runtimeShim())
                .contains("#include <freertos/semphr.h>", "xTaskCreateStaticPinnedToCore", "juno_thread_backedge")
                .doesNotContain("juno_preempt_tick");
    }

    @Test
    @DisplayName("A thread program assembles with the ESP32-S3 toolchain")
    void shouldAssembleThreadsWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compileSource("demo.NanoThreads", THREADS);

        // When & Then
        assemble(gcc, "NanoThreads", result);
    }

    @Test
    @DisplayName("Rejects a call that needs more than six argument words")
    void shouldRejectMoreThanSixArgumentWords() {
        // When & Then
        assertThatThrownBy(() -> compileSource("demo.NanoWide", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoNanoEsp32.class)
                public final class NanoWide {
                    static int sum(int a, int b, int c, int d, int e, int f, int g) {
                        return a <= 0 ? b + c + d + e + f + g : sum(a - 1, b, c, d, e, f, g);
                    }
                    public static void main(String[] args) {
                        Serial.println(sum(MCU.getGpio().analogRead(0), 1, 2, 3, 4, 5, 6));
                    }
                }
                """))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("more than 6 argument words");
    }

    private void assemble(String gcc, String name, CompilationResult result) throws Exception {
        Path assembly = temporaryDirectory.resolve(name + ".S");
        Files.writeString(assembly, result.assembly(), StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(gcc, "-mlongcalls", "-x", "assembler-with-cpp", "-c",
                assembly.toString(), "-o", temporaryDirectory.resolve(name + ".o").toString())
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        Assumptions.assumeTrue(finished, "xtensa-esp32s3-elf-gcc timed out");
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(diagnostics).isZero();
    }

    /** The ESP32-S3 GCC arduino-cli installs with the {@code arduino:esp32} core, when present. */
    private @Nullable String availableXtensaGcc() {
        Path packagesRoot = Path.of(System.getProperty("user.home"), "Library", "Arduino15", "packages");
        if (!Files.isDirectory(packagesRoot)) {
            packagesRoot = Path.of(System.getProperty("user.home"), ".arduino15", "packages");
        }
        if (!Files.isDirectory(packagesRoot)) {
            return null;
        }
        try (Stream<Path> found = Files.walk(packagesRoot, 6)) {
            return found.filter(path -> path.getFileName().toString().equals("xtensa-esp32s3-elf-gcc")
                            && Files.isExecutable(path) && !Files.isDirectory(path))
                    .max(Comparator.comparing(Path::toString))
                    .map(Path::toString)
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }
}
