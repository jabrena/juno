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

    private static final String R3 = Board.UNO_R3.id();

    @TempDir
    Path temporaryDirectory;

    private CompilationResult compile(String className, String body, String imports) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                %s
                @Board(ArduinoUnoR3Board.class)
                public final class %s {
                    public static void main(String[] args) {
                %s
                    }
                }
                """.formatted(imports, simpleName, body);
        Path classes = CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, className, source);
        return new JunoCompiler().compile(CompilerTestSupport.classpath(classes), className, false, Optional.of(R3));
    }

    private CompilationResult compileR3(String className, String body, String imports) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                %s
                @Board(ArduinoUnoR3Board.class)
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
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoUnoR3Board")).isEqualTo(board);
        assertThat(board.annotationArgument()).isEqualTo("ArduinoUnoR3Board.class");
    }

    @Test
    @DisplayName("Rejects a non-AVR board for the AVR backend")
    void shouldRejectANonAvrBoardForTheAvrBackend() {
        // When & Then
        assertThatThrownBy(() -> new AvrAsmBackend(false, Board.UNO_R4_WIFI, RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R4_WIFI)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an AVR board");
    }

    @Test
    @DisplayName("Compiles basic GPIO for the classic UNO R3")
    void shouldCompileBasicGpioForTheClassicUnoR3() throws Exception {
        // Given
        CompilationResult result = compileR3("demo.R3Blink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), MCU.getGpio().getOutput());
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
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), MCU.getGpio().getOutput());
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
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), MCU.getGpio().getOutput());
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
                import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoUnoR3Board.class)
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
        assertThatThrownBy(() -> compileR3("demo.R3Matrix", "MCU.getLedMatrix().begin();", ""))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3 has no onboard LED matrix");
    }

    @Test
    @DisplayName("Rejects java.net.http on the UNO R3, which has no network support")
    void shouldRejectTheJdkHttpClientOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Http", """
                        try {
                            java.net.http.HttpClient.newHttpClient().send(
                                    java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://example.com/")).build(),
                                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                        } catch (Exception e) {
                            return;
                        }
                """, ""))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3");
    }

    @Test
    @DisplayName("Rejects UDP on the UNO R3, which has no network support")
    void shouldRejectUdpOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Udp", """
                        try {
                            DatagramSocket socket = new DatagramSocket(4210);
                            socket.close();
                        } catch (IOException e) {
                            Serial.println(0);
                        }
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.io.IOException;
                import java.net.DatagramSocket;
                """))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3");
    }

    @Test
    @DisplayName("Rejects TCP on the UNO R3, which has no network support")
    void shouldRejectTcpOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Tcp", """
                        try {
                            InetAddress peer = InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 1, 10});
                            Socket socket = new Socket(peer, 80);
                            socket.close();
                        } catch (IOException e) {
                            Serial.println(0);
                        }
                """, """
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.io.IOException;
                import java.net.InetAddress;
                import java.net.Socket;
                """))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3");
    }

    @Test
    @DisplayName("Rejects the SNTP clock on the UNO R3, which has no network support")
    void shouldRejectTheSntpClockOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3Sntp", "Serial.println(java.time.LocalDateTime.now().getSecond());",
                "import io.github.jabrena.juno.hardware.serial.Serial;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3");
    }

    @Test
    @DisplayName("Rejects an HTTP server on the UNO R3, which has no network support")
    void shouldRejectAnHttpServerOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3HttpServer", "HttpServer.begin(80);",
                "import io.github.jabrena.juno.hardware.net.http.HttpServer;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3");
    }

    @Test
    @DisplayName("Accepts the LCD Keypad Shield on the UNO R3 without networking")
    void shouldAcceptTheLcdKeypadShieldWithoutNetworkingOnTheUnoR3() throws Exception {
        // When
        CompilationResult result = compileR3("demo.R3Lcd", """
                        LcdKeypadShield lcd = new LcdKeypadShield();
                        lcd.print(7, 2);
                """, "import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;");

        // Then
        assertThat(result.runtimeShim()).doesNotContain("#include <WiFi.h>");
    }


    @Test
    @DisplayName("Rejects the board's own Wi-Fi on the UNO R3, which has no radio and no network support")
    void shouldRejectOnboardWifiOnTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileR3("demo.R3OnboardWifi", "MCU.getWifi().begin(\"ssid\", \"password\");",
                "import io.github.jabrena.juno.hardware.net.Wifi;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("MCU.getWifi requires @Board(")
                .hasMessageContaining("UNO R3 has no onboard Wi-Fi radio");
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
        assertThatThrownBy(() -> new RuntimeConfig(2048, OptionalInt.empty()).resolveFor(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xmx can be at most 1024 bytes");
    }

    @Test
    @DisplayName("Enables and refreshes the classic UNO R3 watchdog in the assembly")
    void shouldEnableAndRefreshTheClassicUnoR3WatchdogInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.R3Watchdog", "ArduinoUnoR3Board", R3, 3000);

        // When & Then
        assertThat(result.assembly())
                .contains("call juno_watchdog_begin", "call yield")
                .doesNotContain("call juno_watchdog_refresh");
    }

    @Test
    @DisplayName("The classic UNO R3 watchdog shim uses the AVR wdt API")
    void shouldUseTheAvrWdtApiForTheClassicUnoR3WatchdogShim() throws Exception {
        // Given
        CompilationResult result = compileWatchdog("demo.R3Watchdog", "ArduinoUnoR3Board", R3, 3000);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "#include <avr/wdt.h>",
                "wdt_enable(WDTO_4S);",
                "wdt_reset();",
                "[juno-watchdog] panic: board will reset via watchdog in approximately 4000ms");
    }



    @Test
    @DisplayName("Rejects a classic UNO R3 watchdog timeout outside its chip range")
    void shouldRejectAClassicUnoR3WatchdogTimeoutOutsideItsChipRange() {
        // When & Then
        assertThatThrownBy(() -> compileWatchdog("demo.R3WatchdogTooLong", "ArduinoUnoR3Board", R3, 8001))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("timeoutMillis must be between 1 and 8000 on UNO R3");
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
    @DisplayName("Parses spaced pointer parameter declarations")
    void shouldParseSpacedPointerParameterDeclarations() {
        // Given
        String shim = "extern \"C\" void juno_pointer_spacing(const uint8_t *value) {\n}\n";

        // When
        String thunks = avrShimThunks.generate(shim, Set.of("juno_pointer_spacing"));

        // Then
        assertThat(thunks).contains("reinterpret_cast<const uint8_t *>(static_cast<uintptr_t>(juno_avr_args[0]))");
    }

    @Test
    @DisplayName("Parses float and unnamed word parameter declarations")
    void shouldParseFloatAndUnnamedWordParameterDeclarations() {
        // Given
        String shim = """
                extern "C" void juno_float_parameter(float value   ) {
                }
                extern "C" void juno_unnamed_parameter(uint32_t) {
                }
                """;

        // When
        String thunks = avrShimThunks.generate(shim, Set.of("juno_float_parameter", "juno_unnamed_parameter"));

        // Then
        assertThat(thunks)
                .contains("juno_float_parameter(juno_avr_arg_float(0));")
                .contains("juno_unnamed_parameter(static_cast<uint32_t>(juno_avr_args[0]));");
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
                .hasMessageContaining("UNO R3 has no onboard LED matrix");
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
                .hasMessageContaining("Smtp.send (STARTTLS) requires @Board(ArduinoUnoR4WiFiBoard.class or ArduinoNanoEsp32Board.class or AiThinkerEsp32CamBoard.class or ArduinoPortentaH7Board.class)")
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
        assertThat(RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R3).arenaBytes())
                .isEqualTo(RuntimeLimits.AVR_ARENA_CAPACITY_BYTES);
    }

    @Test
    @DisplayName("Leaves the R4's default runtime config unchanged")
    void shouldLeaveTheR4DefaultRuntimeConfigUnchanged() {
        // When & Then
        assertThat(RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R4_WIFI).arenaBytes()).isEqualTo(RuntimeLimits.ARENA_CAPACITY_BYTES);
    }


    @Test
    @DisplayName("Rejects an arena above the AVR limit")
    void shouldRejectAnArenaAboveTheAvrLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(4096, OptionalInt.empty()).resolveFor(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xmx can be at most 1024 bytes");
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
        assertThat(RuntimeConfig.DEFAULT.resolveFor(Board.UNO_R3)
                .threadStackBytes())
                .isEqualTo(RuntimeLimits.AVR_THREAD_STACK_BYTES);
    }


    @Test
    @DisplayName("Rejects a task stack above the AVR limit")
    void shouldRejectATaskStackAboveTheAvrLimit() {
        // When & Then
        assertThatThrownBy(() -> new RuntimeConfig(RuntimeLimits.AVR_ARENA_CAPACITY_BYTES, OptionalInt.of(2048))
                .resolveFor(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("juno.Xss can be at most 512");
    }

    @Test
    @DisplayName("Reaches the core pin functions through Java-width wrappers")
    void shouldReachTheCorePinFunctionsThroughJavaWidthWrappers() throws Exception {
        // Given
        CompilationResult result = compile("demo.AvrBlink", """
                        MCU.getGpio().pinMode(MCU.getGpio().getBuiltinLed(), MCU.getGpio().getOutput());
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
                // LED_BUILTIN of the classic UNO R3 is D13.
                .contains("ldi r22, 13");
    }

}
