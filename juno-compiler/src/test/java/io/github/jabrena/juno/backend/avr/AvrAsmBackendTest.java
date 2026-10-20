package io.github.jabrena.juno.backend.avr;


import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.AvrShimThunks;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AVR assembly backend")
class AvrAsmBackendTest {
    private final AvrShimThunks avrShimThunks = new AvrShimThunks();

    private static final String R3WIFIREV2 = Board.UNO_R3_WIFI_REV2.id();
    private static final String R3 = Board.UNO_R3.id();

    @TempDir
    Path temporaryDirectory;

    private CompilationResult compile(String className, String body, String imports) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                %s
                @Board(ArduinoUnoR3WiFiRev2.class)
                public final class %s {
                    public static void main(String[] args) {
                %s
                    }
                }
                """.formatted(imports, simpleName, body);
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        return new JunoCompiler().compile(CompilerTestSupport.classpath(classes), className, false, Optional.of(R3WIFIREV2));
    }

    private CompilationResult compileR3(String className, String body, String imports) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                %s
                @Board(ArduinoUnoR3.class)
                public final class %s {
                    public static void main(String[] args) {
                %s
                    }
                }
                """.formatted(imports, simpleName, body);
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        return new JunoCompiler().compile(CompilerTestSupport.classpath(classes), className, false, Optional.of(R3));
    }

    private CompilationResult compileWatchdog(String className, String boardAnnotation, String boardId,
                                              int timeoutMillis) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.%s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.util.Delay;
                @Board(%s.class)
                @Watchdog(timeoutMillis = %d)
                public final class %s {
                    public static void main(String[] args) {
                        while (true) {
                            Delay.millis(1);
                        }
                    }
                }
                """.formatted(boardAnnotation, boardAnnotation, timeoutMillis, simpleName);
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        return new JunoCompiler().compile(CompilerTestSupport.classpath(classes), className, false,
                Optional.of(boardId));
    }

    @Test
    @DisplayName("Resolves the UNO WiFi Rev2 to the AVR backend")
    void shouldResolveTheUnoWifiRev2ToTheAvrBackend() {
        // Given
        Board board = Board.fromId(R3WIFIREV2);

        // When & Then
        assertThat(board).isEqualTo(Board.UNO_R3_WIFI_REV2);
    }

    @Test
    @DisplayName("The UNO WiFi Rev2 has its FQBN and AVR architecture")
    void shouldGiveTheUnoWifiRev2ItsFqbnAndAvrArchitecture() {
        // Given
        Board board = Board.fromId(R3WIFIREV2);

        // When & Then
        assertThat(new String[] {board.fqbn(), board.core().architecture().name()})
                .containsExactly(Board.UNO_R3_WIFI_REV2.fqbn(), Architecture.AVR.name());
    }

    @Test
    @DisplayName("The UNO WiFi Rev2 resolves from its API class name and annotation argument")
    void shouldResolveTheUnoWifiRev2FromItsApiClassNameAndAnnotationArgument() {
        // Given
        Board board = Board.fromId(R3WIFIREV2);

        // When & Then
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoUnoR3WiFiRev2")).isEqualTo(board);
        assertThat(board.annotationArgument()).isEqualTo("ArduinoUnoR3WiFiRev2.class");
    }

    @Test
    @DisplayName("Resolves the classic UNO R3 to the AVR backend")
    void shouldResolveTheClassicUnoR3ToTheAvrBackend() {
        // Given
        Board board = Board.fromId(R3);

        // When & Then
        assertThat(board).isEqualTo(Board.UNO_R3);
    }

    @Test
    @DisplayName("The classic UNO R3 has its FQBN and AVR architecture")
    void shouldGiveTheClassicUnoR3ItsFqbnAndAvrArchitecture() {
        // Given
        Board board = Board.fromId(R3);

        // When & Then
        assertThat(new String[] {board.fqbn(), board.core().architecture().name()})
                .containsExactly(Board.UNO_R3.fqbn(), Architecture.AVR.name());
    }

    @Test
    @DisplayName("The classic UNO R3 resolves from its API class name and annotation argument")
    void shouldResolveTheClassicUnoR3FromItsApiClassNameAndAnnotationArgument() {
        // Given
        Board board = Board.fromId(R3);

        // When & Then
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoUnoR3")).isEqualTo(board);
        assertThat(board.annotationArgument()).isEqualTo("ArduinoUnoR3.class");
    }

    @Test
    @DisplayName("Rejects a non-AVR board for the AVR backend")
    void shouldRejectANonAvrBoardForTheAvrBackend() {
        // When & Then
        assertThatThrownBy(() -> new AvrAsmBackend(false, Board.UNO_R4_WIFI, RuntimeConfig.DEFAULT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an AVR board");
    }

    @Test
    @DisplayName("Compiles basic GPIO for the classic UNO R3")
    void shouldCompileBasicGpioForTheClassicUnoR3() throws Exception {
        // Given
        CompilationResult result = compileR3("demo.R3Blink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), Gpio.getOutput());
                        MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                        Delay.millis(500);
                """, """
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                """);

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_avr_thunk_juno_pin_mode", "call juno_avr_thunk_juno_delay_millis")
                .contains("ldi r22, 13");
    }

    @Test
    @DisplayName("The classic UNO R3 shim omits WiFi, SD soft SPI and context switching")
    void shouldOmitWifiSdSoftSpiAndContextSwitchFromTheClassicUnoR3Shim() throws Exception {
        // Given
        CompilationResult result = compileR3("demo.R3Blink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), Gpio.getOutput());
                        MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                        Delay.millis(500);
                """, """
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                """);

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("WiFiNINA", "JunoSdSoftSpiDriver", "juno_context_switch");
    }

    @Test
    @DisplayName("The classic UNO R3 shim wraps the pin and digital-write calls")
    void shouldWrapThePinAndDigitalWriteCallsInTheClassicUnoR3Shim() throws Exception {
        // Given
        CompilationResult result = compileR3("demo.R3Blink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), Gpio.getOutput());
                        MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                        Delay.millis(500);
                """, """
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("pinMode(static_cast<uint8_t>(pin), static_cast<uint8_t>(mode));")
                .contains("digitalWrite(static_cast<uint8_t>(pin), static_cast<uint8_t>(value != 0 ? HIGH : LOW));");
    }

    @Test
    @DisplayName("Stores a two-byte length in front of every array and reads it back")
    void shouldStoreATwoByteLengthInFrontOfEveryArrayAndReadItBack() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoUnoR3.class)
                public final class R3Length {
                    static int length(byte[] values) {
                        return values.length + values[0];
                    }
                    public static void main(String[] args) {
                        byte[] buffer = new byte[5];
                        Serial.println(length(buffer));
                    }
                }
                """;
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.R3Length", source);
        CompilationResult result = new JunoCompiler().compile(CompilerTestSupport.classpath(classes),
                "demo.R3Length", false, Optional.of(R3));

        // When & Then
        assertThat(result.assembly())
                .as("a 5-byte array is allocated with a 2-byte length word, which is stored and skipped")
                .contains("ldi r22, 7", "std Z+0, r18", "std Z+1, r19", "subi r22, -2", "sbci r23, -1")
                .as("length and bounds check read the word two bytes in front of the elements")
                .contains("sbiw r30, 2", "ld r18, Z+", "ld r19, Z");
    }

    @Test
    @DisplayName("Rejects features that do not fit the classic UNO R3")
    void shouldRejectFeaturesThatDoNotFitTheClassicUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Wifi", "Wifi.status();",
                "import io.github.jabrena.juno.hardware.net.Wifi;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3 has no onboard WiFi module");
    }

    @Test
    @DisplayName("Rejects per-task stacks on the classic UNO R3")
    void shouldRejectPerTaskStacksOnTheClassicUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Tasks", "StructuredTaskScope.open();",
                "import java.util.concurrent.StructuredTaskScope;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3 has no room for per-task stacks");
    }

    @Test
    @DisplayName("Rejects an Xmx above the classic UNO R3's one kilobyte")
    void shouldRejectAnXmxAboveTheClassicUnoR3sOneKilobyte() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(2048, 4, OptionalInt.empty()).forBoard(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xmx can be at most 1024 bytes");
    }

    @Test
    @DisplayName("Enables and refreshes the classic UNO R3 watchdog in the assembly")
    void shouldEnableAndRefreshTheClassicUnoR3WatchdogInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.R3Watchdog", "ArduinoUnoR3", R3, 3000);

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_watchdog_begin", "call yield")
                .doesNotContain("call juno_watchdog_refresh");
    }

    @Test
    @DisplayName("The classic UNO R3 watchdog shim uses the AVR wdt API")
    void shouldUseTheAvrWdtApiForTheClassicUnoR3WatchdogShim() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.R3Watchdog", "ArduinoUnoR3", R3, 3000);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "#include <avr/wdt.h>",
                "wdt_enable(WDTO_4S);",
                "wdt_reset();",
                "[juno-watchdog] panic: board will reset via watchdog in approximately 4000ms");
    }

    @Test
    @DisplayName("Enables and refreshes the UNO WiFi Rev2 watchdog in the assembly")
    void shouldEnableAndRefreshTheUnoWifiRev2WatchdogInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.Rev2Watchdog", "ArduinoUnoR3WiFiRev2", R3WIFIREV2, 3000);

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_watchdog_begin", "call yield")
                .doesNotContain("call juno_watchdog_refresh");
    }

    @Test
    @DisplayName("The UNO WiFi Rev2 watchdog shim uses the megaAVR WDT registers")
    void shouldUseTheMegaAvrWdtRegistersForTheUnoWifiRev2WatchdogShim() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.Rev2Watchdog", "ArduinoUnoR3WiFiRev2", R3WIFIREV2, 3000);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "#include <avr/io.h>",
                "_PROTECTED_WRITE(WDT.CTRLA, WDT_PERIOD_4KCLK_gc);",
                "__asm__ __volatile__(\"wdr\");",
                "[juno-watchdog] panic: board will reset via watchdog in approximately 4100ms");
    }

    @Test
    @DisplayName("Rejects a classic UNO R3 watchdog timeout outside its chip range")
    void shouldRejectAClassicUnoR3WatchdogTimeoutOutsideItsChipRange() {
        // When & Then
        assertThatThrownBy(() -> compileWatchdog("demo.R3WatchdogTooLong", "ArduinoUnoR3", R3, 8001))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("timeoutMillis must be between 1 and 8000 on UNO R3");
    }

    @Test
    @DisplayName("Rejects a UNO WiFi Rev2 watchdog timeout outside its chip range")
    void shouldRejectAUnoWifiRev2WatchdogTimeoutOutsideItsChipRange() {
        // When & Then
        assertThatThrownBy(() -> compileWatchdog("demo.Rev2WatchdogTooLong", "ArduinoUnoR3WiFiRev2", R3WIFIREV2, 8201))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("timeoutMillis must be between 1 and 8200 on UNO WiFi Rev2");
    }

    @Test
    @DisplayName("Generates AVR assembly with the program's entry symbol")
    void shouldGenerateAvrAssemblyWithTheProgramEntrySymbol() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrSmoke", """
                        Serial.println("ready");
                        long big = 1L << 40;
                        double half = 0.5;
                        Serial.println(big + (long) (half * 4));
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.entryPointSymbol()).isEqualTo("juno_AvrSmoke_asm");
    }

    @Test
    @DisplayName("The AVR assembly is generated by the AVR backend with a thunked call")
    void shouldGenerateTheAvrAssemblyFromTheAvrBackendWithAThunkedCall() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrSmoke", """
                        Serial.println("ready");
                        long big = 1L << 40;
                        double half = 0.5;
                        Serial.println(big + (long) (half * 4));
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly())
                .contains("; Generated by Juno's AVR assembly backend")
                .contains(".global juno_avr_args")
                .contains("juno_AvrSmoke_asm:")
                .contains("    push r28")
                .contains("    call juno_avr_thunk_juno_serial_println_str")
                .doesNotContain("bl ")
                .doesNotContain(".word");
    }

    @Test
    @DisplayName("The AVR shim thunks the serial call and defines the software binary64 helpers")
    void shouldThunkTheSerialCallAndDefineTheSoftwareBinary64HelpersInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrSmoke", """
                        Serial.println("ready");
                        long big = 1L << 40;
                        double half = 0.5;
                        Serial.println(big + (long) (half * 4));
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.runtimeShim())
                .contains("typedef uint64_t juno_f64;")
                .contains("extern \"C\" void juno_avr_thunk_juno_serial_println_str() {")
                .contains("juno_serial_println_str(reinterpret_cast<const char*>(static_cast<uintptr_t>(juno_avr_args[0])));")
                .contains("extern \"C\" juno_f64 juno_dmul(juno_f64 a, juno_f64 b)")
                .doesNotContain("Arduino_LED_Matrix.h");
    }

    @Test
    @DisplayName("Lowers int bitwise operations and multiply by a constant")
    void shouldLowerIntBitwiseAndMultiplyByAConstant() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBitwise", """
                        int value = MCU.getGpio().analogRead(0);
                        Serial.println(value & 0x0F);
                        Serial.println(value | 0xF0);
                        Serial.println(value * 8);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly()).contains("    andi r22, 15\n", "    ori r22, 240\n");
    }

    @Test
    @DisplayName("Multiply by a power-of-two constant is a left shift, never a call to the imul helper")
    void shouldLowerMultiplyByAPowerOfTwoConstantAsAShift() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBitwise", """
                        int value = MCU.getGpio().analogRead(0);
                        Serial.println(value & 0x0F);
                        Serial.println(value | 0xF0);
                        Serial.println(value * 8);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        // value * 8 is a power-of-two constant: lowered as a left shift, never a call to juno_avr_imul.
        assertThat(result.assembly()).doesNotContain("call juno_avr_imul");
    }

    @Test
    @DisplayName("Constant shifts are whole-byte moves plus bit-by-bit rotates, never variable helpers")
    void shouldLowerConstantShiftsWithoutTheVariableShiftHelpers() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrShifts", """
                        int value = MCU.getGpio().analogRead(0);
                        Serial.println(value << 10);
                        Serial.println(value >> 10);
                        Serial.println(value >>> 10);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        // A constant shift never calls the variable-shift helpers; it's always whole-byte moves plus
        // bit-by-bit rotates.
        assertThat(result.assembly()).doesNotContain("call juno_avr_ishl", "call juno_avr_ishr",
                "call juno_avr_iushr");
    }

    @Test
    @DisplayName("Lowers the left, arithmetic and logical constant shifts to rotate sequences")
    void shouldLowerTheConstantShiftsToRotateSequences() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrShifts", """
                        int value = MCU.getGpio().analogRead(0);
                        Serial.println(value << 10);
                        Serial.println(value >> 10);
                        Serial.println(value >>> 10);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly()).contains("    lsl r22\n    rol r23\n    rol r24\n    rol r25\n")
                .contains("    asr r25\n    ror r24\n    ror r23\n    ror r22\n")
                .contains("    lsr r25\n    ror r24\n    ror r23\n    ror r22\n");
    }

    @Test
    @DisplayName("Lowers narrowing casts to byte, char and short")
    void shouldLowerNarrowingCastsToByteCharAndShort() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrNarrowing", """
                        int value = MCU.getGpio().analogRead(0);
                        Serial.println((byte) value);
                        Serial.println((char) value);
                        Serial.println((short) value);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly())
                .contains("    mov r23, r22\n    lsl r23\n    sbc r23, r23\n")
                .contains("    ldi r24, 0\n    ldi r25, 0\n")
                .contains("    mov r24, r23\n    lsl r24\n    sbc r24, r24\n    mov r25, r24\n");
    }

    @Test
    @DisplayName("Compares with greater-than and less-or-equal on AVR")
    void shouldCompareWithGreaterThanAndLessOrEqualOnAvr() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrCompare", """
                        int value = MCU.getGpio().analogRead(0);
                        if (value > 10) {
                            Serial.println("big");
                        }
                        if (value <= 10) {
                            Serial.println("small");
                        }
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        // GREATER_THAN/LESS_EQUAL swap their operands and reuse brlt/brge, AVR having only signed
        // less-than/greater-or-equal.
        assertThat(result.assembly()).contains("brlt").contains("brge");
    }

    @Test
    @DisplayName("Lowers long and floating-point wide operations on AVR")
    void shouldLowerLongAndFloatingPointWideOperationsOnAvr() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrWide", """
                        long left = MCU.getGpio().analogRead(0);
                        long right = MCU.getGpio().analogRead(1);
                        Serial.println(-left);
                        Serial.println(left > right);
                        int narrowed = (int) left;
                        long widened = narrowed;
                        Serial.println(widened);

                        float fa = MCU.getGpio().analogRead(2);
                        float fb = MCU.getGpio().analogRead(3);
                        Serial.println(fa + fb);
                        Serial.println(fa == fb);
                        Serial.println((int) fa);

                        double da = MCU.getGpio().analogRead(4);
                        Serial.println(-da);
                        Serial.println(da == fb);
                        Serial.println((float) da);
                        Serial.println((long) fa);
                        Serial.println((float) left);
                """, "import io.github.jabrena.juno.hardware.gpio.Gpio;\nimport io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_avr_thunk_juno_lneg")
                .contains("call juno_avr_thunk_juno_lcmp")
                .contains("call juno_avr_thunk_juno_fadd")
                .contains("call juno_avr_thunk_juno_fcmp")
                .contains("call juno_avr_thunk_juno_f2i")
                .contains("    subi r25, 0x80\n") // DoubleNegate's sign flip, shared text with FloatNegate.
                .contains("call juno_avr_thunk_juno_dcmp")
                .contains("call juno_avr_thunk_juno_d2f")
                .contains("call juno_avr_thunk_juno_f2l")
                .contains("call juno_avr_thunk_juno_l2f");
    }

    @Test
    @DisplayName("Uses software SPI for shield SD cards in the shim")
    void shouldUseSoftwareSpiForShieldSdCardsInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrSd", "SdCard.begin();",
                "import io.github.jabrena.juno.hardware.device.sd.SdCard;");

        // When & Then
        assertThat(result.runtimeShim())
                .contains("class JunoSdSoftSpiDriver : public SdSpiBaseClass")
                .contains("digitalWrite(11, (output & mask) != 0 ? HIGH : LOW);")
                .contains("digitalWrite(13, HIGH);")
                .contains("digitalRead(12)")
                .contains("JUNO_PROPERTIES_VALUE_CAPACITY = 64")
                .contains("JUNO_PROPERTIES_LINE_CAPACITY = 96")
                .contains("SdSpiConfig(chipSelectPin, SHARED_SPI, SD_SCK_MHZ(0), &juno_sd_spi)")
                .doesNotContain("juno_sd.begin(chipSelectPin)");
    }

    @Test
    @DisplayName("Passes a long argument on an even word, like AAPCS")
    void shouldPassALongArgumentOnAnEvenWordLikeAapcs() {
        // Given
        String shim = """
                extern "C" void juno_string_concat_append_long(int32_t handle, int64_t value) {
                }
                extern "C" JUNO_ASM_ABI juno_f64 juno_dcmp_like(juno_f64 a, juno_f64 b, int32_t nanResult) {
                }
                extern "C" void* juno_alloc_like(uint32_t size) {
                }
                """;

        // When
        String thunks = avrShimThunks.generate(shim,
                new java.util.TreeSet<>(Set.of("juno_string_concat_append_long", "juno_dcmp_like", "juno_alloc_like")));

        // Then
        assertThat(thunks)
                .contains("juno_string_concat_append_long(static_cast<int32_t>(juno_avr_args[0]), "
                        + "static_cast<int64_t>(juno_avr_arg64(2)));")
                .contains("extern \"C\" uint64_t juno_avr_thunk_juno_dcmp_like() {")
                .contains("juno_dcmp_like(static_cast<juno_f64>(juno_avr_arg64(0)), "
                        + "static_cast<juno_f64>(juno_avr_arg64(2)), static_cast<int32_t>(juno_avr_args[4]))")
                .contains("return static_cast<int32_t>(reinterpret_cast<intptr_t>(juno_alloc_like(");
    }

    @Test
    @DisplayName("Refuses a thunk for a C double or a missing prototype")
    void shouldRefuseAThunkForACDoubleOrAMissingPrototype() {
        // When & Then
        assertThatThrownBy(() -> avrShimThunks.generate("extern \"C\" void f(double value) {\n}\n", Set.of("f")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("avr-gcc makes 32 bits wide");
    }

    @Test
    @DisplayName("Refuses a thunk whose prototype has no extern C definition")
    void shouldRefuseAThunkWhoseNameHasNoExternCDefinition() {
        // When & Then
        assertThatThrownBy(() -> avrShimThunks.generate("", Set.of("juno_missing")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("no extern \"C\" definition of juno_missing");
    }

    @Test
    @DisplayName("Rejects capabilities the board lacks")
    void shouldRejectCapabilitiesTheBoardLacks() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.AvrMatrix", "MCU.getLedMatrix().begin();",
                "import io.github.jabrena.juno.hardware.MCU;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO WiFi Rev2 has no onboard LED matrix");
    }

    @Test
    @DisplayName("Rejects the single-precision-only libm for double math")
    void shouldRejectDoubleMathBecauseOnlySinglePrecisionLibmIsAvailable() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.AvrSine", "Serial.println((int) Math.sin(1.0));",
                "import io.github.jabrena.juno.hardware.serial.Serial;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("only has single-precision libm");
    }

    @Test
    @DisplayName("Rejects Mouse on a board without a native USB port")
    void shouldRejectMouseOnABoardWithoutANativeUsbPort() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.AvrMouse", "MCU.getMouse().begin();",
                "import io.github.jabrena.juno.hardware.hid.Mouse;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("no native USB port");
    }

    @Test
    @DisplayName("Accepts HTTP and UDP on the UNO WiFi Rev2")
    void shouldAcceptHttpAndUdpOnTheUnoWifiRev2() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrNet", """
                        byte[] body = new byte[32];
                        byte[] headers = new byte[32];
                        int[] status = new int[2];
                        Wifi.begin("ssid", "password");
                        Serial.println(HttpClient.get("http://example.com/", body, 32, headers, 32, status));
                        Serial.println(Udp.listen(4210));
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import io.github.jabrena.juno.hardware.net.Udp;
                import io.github.jabrena.juno.hardware.net.Wifi;
                import io.github.jabrena.juno.hardware.net.http.HttpClient;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("#include <WiFiNINA.h>")
                .contains("#include <WiFiUdp.h>")
                .contains("static WiFiUDP juno_udp;")
                .contains("extern \"C\" int32_t juno_avr_thunk_juno_http_get() {");
    }

    @Test
    @DisplayName("Runs HTTPS through the NINA module's TLS")
    void shouldRunHttpsThroughTheNinaModulesTls() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTls", """
                        byte[] body = new byte[32];
                        int[] status = new int[2];
                        Serial.println(HttpClient.get("https://example.com/", body, 32, body, 32, status));
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import io.github.jabrena.juno.hardware.net.http.HttpClient;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("#include <WiFiNINA.h>")
                .contains("using JunoHttpsClient = WiFiSSLClient;")
                .contains("extern \"C\" int32_t juno_avr_thunk_juno_https_get() {")
                .doesNotContain("WiFiSSLClient.h");
    }

    @Test
    @DisplayName("Sends and reads mail over implicit TLS like the UNO R4")
    void shouldSendAndReadMailOverImplicitTlsLikeTheUnoR4() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrMail", """
                        Serial.println(Smtp.sendTls("smtp.example.com", 465, "user", "secret", "a@example.com",
                                "b@example.com", "Hello", "Body"));
                        Serial.println(Pop3Client.messageCount("pop.example.com", 995, "user", "secret"));
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import io.github.jabrena.juno.hardware.net.email.Pop3Client;
                import io.github.jabrena.juno.hardware.net.email.Smtp;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("extern \"C\" int32_t juno_avr_thunk_juno_smtp_send_tls() {")
                .contains("extern \"C\" int32_t juno_avr_thunk_juno_pop3_message_count() {")
                .contains("WiFiSSLClient client;")
                .doesNotContain("ESP_SSLClient");
    }

    @Test
    @DisplayName("Rejects STARTTLS mail submission")
    void shouldRejectStartTlsMailSubmission() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.AvrStartTls", """
                        Serial.println(Smtp.send("smtp.example.com", 587, "user", "secret", "a@example.com",
                                "b@example.com", "Hello", "Body"));
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import io.github.jabrena.juno.hardware.net.email.Smtp;
                """))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Smtp.send (STARTTLS) requires @Board(ArduinoUnoR4WiFi.class)")
                .hasMessageContaining("use Smtp.sendTls with implicit TLS, usually port 465");
    }

    @Test
    @DisplayName("Converts big numbers to doubles on the software binary64 in the shim")
    void shouldConvertBigNumbersToDoublesOnTheSoftwareBinary64InTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBig", """
                        BigDecimal price = new BigDecimal("19.99");
                        Serial.println(price.multiply(BigDecimal.valueOf(3)).toString());
                        Serial.println(price.doubleValue() > 19.0 ? 1 : 0);
                        Serial.println(BigInteger.TEN.pow(30).doubleValue() > 1.0 ? 1 : 0);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.math.BigDecimal;
                import java.math.BigInteger;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("extern \"C\" juno_f64 juno_big_decimal_double_value(int32_t a) {")
                .contains("extern \"C\" juno_f64 juno_big_integer_double_value(int32_t a) {")
                .doesNotContain("strtod");
    }

    @Test
    @DisplayName("Lowers UUID wide values through AVR thunks on both R3 boards")
    void shouldLowerUuidWideValuesThroughAvrThunksOnBothR3Boards() throws Exception {
        // Given
        String body = """
                        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                        UUID copy = new UUID(value.getMostSignificantBits(), value.getLeastSignificantBits());
                        Serial.println(copy.toString());
                        Serial.println(copy.equals(value));
                """;
        String imports = """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.UUID;
                """;

        for (CompilationResult result : List.of(
                compile("demo.AvrUuid", body, imports), compileR3("demo.ClassicAvrUuid", body, imports))) {
            // When & Then
            assertThat(result.assembly()).contains(
                    "call juno_avr_thunk_juno_uuid_new",
                    "call juno_avr_thunk_juno_uuid_get_most_significant_bits",
                    "call juno_avr_thunk_juno_uuid_get_least_significant_bits");
        }
    }

    @Test
    @DisplayName("The UUID shims on both R3 boards declare the thunks and the helpers")
    void shouldDeclareTheUuidThunksAndHelpersInTheShimsOnBothR3Boards() throws Exception {
        // Given
        String body = """
                        UUID value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                        UUID copy = new UUID(value.getMostSignificantBits(), value.getLeastSignificantBits());
                        Serial.println(copy.toString());
                        Serial.println(copy.equals(value));
                """;
        String imports = """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.UUID;
                """;

        for (CompilationResult result : List.of(
                compile("demo.AvrUuid", body, imports), compileR3("demo.ClassicAvrUuid", body, imports))) {
            // When & Then
            assertThat(result.runtimeShim()).contains(
                    "extern \"C\" int32_t juno_avr_thunk_juno_uuid_new()",
                    "extern \"C\" uint64_t juno_avr_thunk_juno_uuid_get_most_significant_bits()",
                    "juno_uuid_from_string", "juno_uuid_to_string");
        }
    }

    @Test
    @DisplayName("Sizes the arena for six kilobytes of RAM")
    void shouldSizeTheArenaForSixKilobytesOfRam() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.forBoard(Board.UNO_R3_WIFI_REV2).arenaBytes())
                .isEqualTo(RuntimeLimits.AVR_ARENA_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("Leaves the R4's default runtime config unchanged")
    void shouldLeaveTheR4DefaultRuntimeConfigUnchanged() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.forBoard(Board.UNO_R4_WIFI)).isEqualTo(RuntimeConfig.DEFAULT);
    }

    @Test
    @DisplayName("Keeps an explicit arena that fits the UNO WiFi Rev2")
    void shouldKeepAnExplicitArenaThatFitsTheUnoWifiRev2() {
        // Given
        RuntimeConfig explicit = new RuntimeConfig(2048, 4, OptionalInt.empty());

        // When & Then
        assertThat(explicit.forBoard(Board.UNO_R3_WIFI_REV2)).isEqualTo(explicit);
    }

    @Test
    @DisplayName("Rejects an arena above the AVR limit")
    void shouldRejectAnArenaAboveTheAvrLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(4096, 4, OptionalInt.empty()).forBoard(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("at most " + RuntimeLimits.AVR_MAX_ARENA_BYTES);
    }

    @Test
    @DisplayName("Forks tasks on the preemptive scheduler with an AVR context switch")
    void shouldForkTasksOnThePreemptiveSchedulerWithAnAvrContextSwitch() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTasks", """
                        final int[] sums = new int[2];
                        try (var scope = StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                for (int i = 0; i < 10; i++) {
                                    sums[0] = sums[0] + i;
                                }
                            });
                            scope.fork(() -> { sums[1] = 7; });
                            scope.join();
                        } catch (Exception failure) {
                            Serial.println("failed");
                        }
                        Serial.println(sums[0] + sums[1]);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.StructuredTaskScope;
                """);

        // When & Then
        // The scheduler enters a task through a global symbol that takes its argument in r22-r25.
        assertThat(result.assembly())
                .contains("""
                            .global juno_thread_entry
                            .type juno_thread_entry, @function
                        juno_thread_entry:
                            push r2
                        """)
                .contains("    sts juno_avr_args+0, r22\n    sts juno_avr_args+1, r23\n")
                .contains("    call juno_thread_backedge\n")
                // Every method but the entry point is a safepoint too, right after its arguments are in its frame.
                .contains("    sts juno_avr_args+0, r22\n    sts juno_avr_args+1, r23\n")
                .containsPattern("(?s)juno_thread_entry:\n.*?call juno_thread_safepoint\n")
                .contains("    call juno_thread_main_exit\n")
                .contains("    lds r26, juno_stack_limit\n")
                .doesNotContain("call yield\n");
    }

    @Test
    @DisplayName("Main starts before any thread exists")
    void shouldStartMainBeforeAnyThreadExists() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTasks", """
                        final int[] sums = new int[2];
                        try (var scope = StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                for (int i = 0; i < 10; i++) {
                                    sums[0] = sums[0] + i;
                                }
                            });
                            scope.fork(() -> { sums[1] = 7; });
                            scope.join();
                        } catch (Exception failure) {
                            Serial.println("failed");
                        }
                        Serial.println(sums[0] + sums[1]);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.StructuredTaskScope;
                """);
        String entry = result.assembly().substring(result.assembly().indexOf(result.entryPointSymbol() + ":\n"));

        // When & Then
        assertThat(entry.substring(0, entry.indexOf("    .type ")))
                .as("main() starts before any thread exists").doesNotContain("juno_thread_safepoint");
    }

    @Test
    @DisplayName("The AVR thread port sizes its stacks and context switch in the shim")
    void shouldSizeTheAvrThreadPortStacksAndContextSwitchInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTasks", """
                        final int[] sums = new int[2];
                        try (var scope = StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                for (int i = 0; i < 10; i++) {
                                    sums[0] = sums[0] + i;
                                }
                            });
                            scope.fork(() -> { sums[1] = 7; });
                            scope.join();
                        } catch (Exception failure) {
                            Serial.println("failed");
                        }
                        Serial.println(sums[0] + sums[1]);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.StructuredTaskScope;
                """);

        // When & Then
        assertThat(result.runtimeShim())
                .contains("---- Juno threads: AVR port")
                .contains("static constexpr uint16_t JUNO_THREAD_STACK_BYTES = " + RuntimeLimits.AVR_THREAD_STACK_BYTES + "u;")
                .contains("\"  out 0x3e, r23\\n\"")
                .contains("juno_gc_scan_bytes(from, reinterpret_cast<const uint8_t*>(top));")
                .contains("juno_thread_gc_scan(&stackMarker);");
    }

    @Test
    @DisplayName("The AVR thread shim catches an overflow with the stack pointer and the canary")
    void shouldCatchAnAvrThreadStackOverflowWithTheStackPointerAndTheCanary() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTasks", """
                        final int[] sums = new int[2];
                        try (var scope = StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                for (int i = 0; i < 10; i++) {
                                    sums[0] = sums[0] + i;
                                }
                            });
                            scope.fork(() -> { sums[1] = 7; });
                            scope.join();
                        } catch (Exception failure) {
                            Serial.println("failed");
                        }
                        Serial.println(sums[0] + sums[1]);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.StructuredTaskScope;
                """);

        // When & Then
        // An overflow is caught by the stack pointer as well as the canary, and reported from its own stack.
        assertThat(result.runtimeShim())
                .contains("extern \"C\" [[noreturn]] void juno_thread_stack_overflow() {")
                .contains("bool below = SP < reinterpret_cast<uintptr_t>(bottom) + sizeof JUNO_STACK_CANARY;",
                        "static uint8_t juno_overflow_stack[128];", "\"out __SP_H__, %B0\\n\"");
    }

    @Test
    @DisplayName("Preempts at safepoints once the slice is over, with no tick or interrupt trampoline")
    void shouldPreemptAtSafepointsOnceTheSliceIsOverWithNoTick() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrTasks", """
                        final int[] sums = new int[2];
                        try (var scope = StructuredTaskScope.open()) {
                            scope.fork(() -> {
                                for (int i = 0; i < 10; i++) {
                                    sums[0] = sums[0] + i;
                                }
                            });
                            scope.fork(() -> { sums[1] = 7; });
                            scope.join();
                        } catch (Exception failure) {
                            Serial.println("failed");
                        }
                        Serial.println(sums[0] + sums[1]);
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.StructuredTaskScope;
                """);

        // When & Then
        // Preemption at safepoints once the time slice is over; no tick, so no interrupt trampoline.
        assertThat(result.runtimeShim())
                .contains("extern \"C\" void juno_thread_safepoint() {",
                        "if (now - juno_slice_start < JUNO_TIME_SLICE_MILLIS) return;")
                .doesNotContain("juno_preempt_trampoline", "SysTick_Handler", "k_timer");
    }

    @Test
    @DisplayName("Keeps the stack-limit check out of programs without tasks")
    void shouldKeepTheStackLimitCheckOutOfProgramsWithoutTasks() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrNoTasks", """
                        for (int i = 0; i < 3; i++) {
                            Serial.println(i);
                        }
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.assembly())
                .contains("    call yield\n")
                .doesNotContain("juno_stack_limit")
                .doesNotContain("juno_thread_main_exit")
                .doesNotContain("juno_thread_safepoint");
    }

    @Test
    @DisplayName("A program without tasks has no context switch in its shim")
    void shouldOmitTheContextSwitchFromTheShimOfAProgramWithoutTasks() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrNoTasks", """
                        for (int i = 0; i < 3; i++) {
                            Serial.println(i);
                        }
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_context_switch");
    }

    @Test
    @DisplayName("Sizes task stacks for six kilobytes of RAM")
    void shouldSizeTaskStacksForSixKilobytesOfRam() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.forBoard(Board.UNO_R3_WIFI_REV2)
                .threadStackBytes(Board.UNO_R3_WIFI_REV2.core().defaultThreadStackBytes()))
                .isEqualTo(RuntimeLimits.AVR_THREAD_STACK_BYTES);
    }

    @Test
    @DisplayName("An explicit task stack of one kilobyte is kept on the UNO WiFi Rev2")
    void shouldKeepAnExplicitOneKilobyteTaskStackOnTheUnoWifiRev2() {
        // Given
        RuntimeConfig larger = new RuntimeConfig(RuntimeConfig.DEFAULT.arenaBytes(), 4, OptionalInt.of(1024));

        // When & Then
        assertThat(larger.forBoard(Board.UNO_R3_WIFI_REV2).threadStackBytes()).hasValue(1024);
    }

    @Test
    @DisplayName("Rejects a task stack above the AVR limit")
    void shouldRejectATaskStackAboveTheAvrLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(RuntimeConfig.DEFAULT.arenaBytes(), 4, OptionalInt.of(2048))
                .forBoard(Board.UNO_R3_WIFI_REV2))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xss can be at most " + RuntimeLimits.AVR_MAX_THREAD_STACK_BYTES);
    }

    @Test
    @DisplayName("Reaches the core pin functions through Java-width wrappers")
    void shouldReachTheCorePinFunctionsThroughJavaWidthWrappers() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBlink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), Gpio.getOutput());
                        MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                        Delay.millis(500);
                """, """
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                """);

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_avr_thunk_juno_pin_mode")
                .contains("call juno_avr_thunk_juno_digital_write")
                .contains("call juno_avr_thunk_juno_delay_millis")
                // LED_BUILTIN of the uno2018 variant.
                .contains("ldi r22, 25");
    }

    @Test
    @DisplayName("The shim wraps the pinMode call with a PinMode cast")
    void shouldWrapThePinModeCallWithAPinModeCastInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBlink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), Gpio.getOutput());
                        MCU.getGpio().digitalWrite(MCU.getGpio().getBuiltinLed(), true);
                        Delay.millis(500);
                """, """
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                """);

        // When & Then
        assertThat(List.of(result.runtimeShim().split("\n")))
                .contains("  pinMode(static_cast<uint8_t>(pin), static_cast<PinMode>(mode));");
    }
}
