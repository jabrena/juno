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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Xtensa assembly backend")
class XtensaAsmBackendTest {
    private static final String NANO_ESP32 = Board.NANO_ESP32.id();

    private static final String BLINK = """
                    DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());
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
                    long value = ArduinoUno.getGpio().analogRead(0);
                    long other = ArduinoUno.getGpio().analogRead(1) + 1L;
                    int shift = ArduinoUno.getGpio().analogRead(2);
                    long mixed = value * value + other - value / other + value % other;
                    mixed = (mixed & other) | (mixed ^ value);
                    mixed = (mixed << shift) + (mixed >> shift) + (mixed >>> shift) - mixed;
                    if (mixed > other) {
                        mixed = -mixed;
                    }
                    Serial.println((int) (mixed + 5_000_000_000L));
            """;
    private static final String FLOATS = """
                    int raw = ArduinoUno.getGpio().analogRead(3);
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
                    double sample = ArduinoUno.getGpio().analogRead(4);
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
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.ArduinoNano;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32Board.class)
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
                    int raw = ArduinoNano.getGpio().analogRead(0);
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
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.ArduinoUno;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32Board.class)
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
                    int limit = ArduinoUno.getGpio().analogRead(0);
                    Total total = new Total();
                    Thread first = Thread.startVirtualThread(new Counter(total, limit));
                    Thread second = Thread.startVirtualThread(new Counter(total, limit));
                    first.join();
                    second.join();
                    Serial.println(total.value);
                }
            }
            """;

    /** Recursive, so neither helper is inlined and every call really passes its words. */
    private static final String MANY_ARGUMENTS = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.ArduinoUno;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32Board.class)
            public final class NanoWide {
                static int sum(int a, int b, int c, int d, int e, int f, int g) {
                    return a <= 0 ? b + c + d + e + f + g : sum(a - 1, b, c, d, e, f, g);
                }
                static long mixed(int a, int b, int c, int d, int e, long f, int g, int h) {
                    return a <= 0 ? f + g * 3 + h * 5 + b + c + d + e : mixed(a - 1, b, c, d, e, f, g, h);
                }
                public static void main(String[] args) {
                    int raw = ArduinoUno.getGpio().analogRead(0);
                    Serial.println(sum(raw, 1, 2, 3, 4, 5, 6));
                    Serial.println(mixed(raw, raw + 1, 2, 3, 4, raw * 1000L, raw + 7, raw + 8));
                }
            }
            """;

    private static final String SHAPES = """
            package demo;
            import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
            import io.github.jabrena.juno.annotations.Board;
            import io.github.jabrena.juno.hardware.ArduinoUno;
            import io.github.jabrena.juno.hardware.serial.Serial;
            @Board(ArduinoNanoEsp32Board.class)
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
                    int value = ArduinoUno.getGpio().analogRead(0);
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
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                %s
                @Board(ArduinoNanoEsp32Board.class)
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
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoNanoEsp32Board"))
                .isEqualTo(Board.NANO_ESP32);
        assertThat(Board.NANO_ESP32.annotationArgument()).isEqualTo("ArduinoNanoEsp32Board.class");
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
                .contains("extern \"C\" void juno_pin_mode(int32_t pin, int32_t mode) {\n"
                        + "  pinMode(juno_nano_pin(pin), mode == 1 ? OUTPUT : mode == 2 ? INPUT_PULLUP : INPUT);")
                .contains("extern \"C\" void juno_digital_write(int32_t pin, int32_t value) {")
                .doesNotContain("extern \"C\" void yield()", "Arduino_LED_Matrix");
    }

    @Test
    @DisplayName("Juno's A0-A5 numbers (14-19) reach the Nano's A0-A5 instead of its RGB LED pins")
    void shouldTranslateAnalogPinNumbersToTheNanosOwn() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoAnalog", """
                        Serial.begin(BaudRate.BAUD_115200);
                        Serial.println(ArduinoUno.getGpio().analogRead(Gpio.getA0()));
                """, SERIAL_IMPORTS + "import io.github.jabrena.juno.hardware.gpio.Gpio;\n");

        // When & Then
        assertThat(result.assembly()).contains("movi a10, 14\n", "call8 juno_analog_read\n");
        assertThat(result.runtimeShim())
                .contains("A0, A1, A2, A3, A4, A5", "return pin >= 0 && pin < 20 ? pins[pin] : pin;")
                .contains("return analogRead(juno_nano_pin(pin));", "digitalWrite(juno_nano_pin(pin), value);")
                .contains("return digitalRead(juno_nano_pin(pin));", "analogWrite(juno_nano_pin(pin), value);");
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
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoNanoEsp32Board.class)
                public final class NanoCalls {
                    static int mix(int a, int b, int c) {
                        return a <= 0 ? b - c : mix(a - 1, a * b, c);
                    }
                    public static void main(String[] args) {
                        Serial.println(mix(ArduinoUno.getGpio().analogRead(0), 7, 2));
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
                        int value = ArduinoUno.getGpio().analogRead(0);
                        int divisor = ArduinoUno.getGpio().analogRead(1);
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
                        Serial.println(ArduinoUno.getBoardModel().isR4() ? 1 : 0);
                """, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly()).contains("_asmblock0:\n    movi a8, 0\n");
    }

    @Test
    @DisplayName("Lowers the onboard Wi-Fi station calls through the WiFi.h shim helpers")
    void shouldLowerOnboardWifi() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoWifi", """
                        ArduinoUno.getWifi().begin("net", "secret");
                        Serial.println(ArduinoUno.getWifi().status());
                """, "import io.github.jabrena.juno.hardware.ArduinoUno; import io.github.jabrena.juno.hardware.serial.Serial;"
                + " import io.github.jabrena.juno.hardware.serial.BaudRate;");

        // Then
        assertThat(result.runtimeShim()).contains("#include <WiFi.h>").contains("juno_wifi_begin");
        assertThat(result.runtimeShim()).doesNotContain("WiFiS3.h").doesNotContain("beginAP");
    }

    @Test
    @DisplayName("Lowers Mouse to the core's TinyUSB USBHIDMouse instead of the Mouse library")
    void shouldLowerMouseToTheCoreUsbHidMouse() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoMouse", """
                        Mouse mouse = ArduinoNano.getMouse();
                        mouse.begin();
                        mouse.move(50, -50);
                """, "import io.github.jabrena.juno.hardware.ArduinoNano; import io.github.jabrena.juno.hardware.hid.Mouse;");

        // Then
        assertThat(result.assembly()).contains("call8 juno_mouse_begin\n", "call8 juno_mouse_move\n");
        assertThat(result.runtimeShim())
                .contains("#include <USB.h>\n#include <USBHIDMouse.h>\n", "static JunoUsbMouse Mouse;",
                        "extern \"C\" void juno_mouse_move(int32_t x, int32_t y)")
                .doesNotContain("#include <Mouse.h>");
    }

    @Test
    @DisplayName("Lowers a plain HTTP GET through the shared HTTP codec over the WiFi.h client")
    void shouldLowerPlainHttpOnTheWifiClient() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoHttp", """
                        byte[] body = new byte[64];
                        byte[] headers = new byte[32];
                        int[] statusAndHeadersLength = new int[2];
                        HttpClient.get("http://example.com/", body, body.length, headers, headers.length,
                                statusAndHeadersLength);
                """, "import io.github.jabrena.juno.hardware.net.http.HttpClient;");

        // Then
        assertThat(result.runtimeShim()).contains("juno_http_get(");
        assertThat(result.runtimeShim()).contains("#include <WiFi.h>");
        assertThat(result.runtimeShim()).doesNotContain("WiFiSSLClient");
    }

    @Test
    @DisplayName("Lowers an HTTPS request through a WiFiClientSecure that checks Mozilla's root certificates")
    void shouldLowerHttpsOnTheWifiClientSecure() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoHttps", """
                        byte[] path = new byte[16];
                        byte[] noHeaders = new byte[1];
                        byte[] body = new byte[64];
                        byte[] headers = new byte[32];
                        int[] statusAndHeadersLength = new int[2];
                        HttpClient.requestHttps("example.com", 443, "GET", path, 1, noHeaders, 0, null, -1, body,
                                body.length, headers, headers.length, statusAndHeadersLength);
                """, "import io.github.jabrena.juno.hardware.net.http.HttpClient;");

        // Then
        assertThat(result.runtimeShim()).contains("#include <WiFiClientSecure.h>",
                "struct JunoEspTlsClient : WiFiClientSecure",
                "setCACertBundle(juno_ca_bundle, sizeof(juno_ca_bundle))",
                "static const uint8_t juno_ca_bundle[] = {", "juno_https_");
        assertThat(result.runtimeShim()).doesNotContain("setInsecure()");
        assertThat(result.runtimeShim()).doesNotContain("WiFiSSLClient");
    }

    @Test
    @DisplayName("Lowers HttpServer through a WiFiServer transport with placement new")
    void shouldLowerHttpServerOnTheWifiServer() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoServer", """
                        byte[] body = new byte[64];
                        HttpServer.begin(80);
                        if (HttpServer.accept(body, body.length) >= 0) {
                            HttpServer.respond(200, "text/plain", "online");
                        }
                """, "import io.github.jabrena.juno.hardware.net.http.HttpServer;");

        // Then
        assertThat(result.runtimeShim()).contains("juno_http_server_begin(", "WiFiServer(static_cast<uint16_t>(port))",
                "#include <new>");
        assertThat(result.runtimeShim()).doesNotContain("BridgeTCPServer", "mbedtls", "juno_http_server_begin_tls");
    }

    @Test
    @DisplayName("Lowers HttpServer.beginTls to an mbedTLS server session over the WiFiServer")
    void shouldLowerHttpServerBeginTlsToAnMbedTlsServerSession() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoHttpsServer", """
                        byte[] certificate = new byte[1024];
                        byte[] key = new byte[512];
                        byte[] body = new byte[64];
                        if (HttpServer.beginTls(443, certificate, 900, key, 240)) {
                            if (HttpServer.accept(body, body.length) >= 0) {
                                HttpServer.respond(200, "text/plain", "secure");
                            }
                        }
                """, "import io.github.jabrena.juno.hardware.net.http.HttpServer;");

        // Then
        assertThat(result.assembly()).contains("juno_http_server_begin_tls", "juno_http_server_accept");
        assertThat(result.runtimeShim()).contains("#include <mbedtls/ssl.h>",
                "extern \"C\" int32_t juno_http_server_begin_tls(int32_t port, const uint8_t* certificate,",
                "MBEDTLS_SSL_IS_SERVER", "class JunoHttpsServerClient",
                "using JunoHttpClient = JunoHttpsServerClient;", "return juno_http_server_client.open(client);",
                "#if MBEDTLS_VERSION_MAJOR >= 3", "extern \"C\" int32_t juno_http_server_accept(");
    }

    @Test
    @DisplayName("Passes SD-sourced strings to Smtp.sendTls as runtime values, in their parameter slots")
    void shouldLowerRuntimeStringsToSmtpSendTls() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoMail", """
                        String host = new StringBuilder().append("smtp.example.com").toString();
                        String user = new StringBuilder().append("user").toString();
                        String password = new StringBuilder().append("secret").toString();
                        String from = new StringBuilder().append("from@example.com").toString();
                        String to = new StringBuilder().append("to@example.com").toString();
                        String subject = new StringBuilder().append("subject").toString();
                        String body = new StringBuilder().append("body").toString();
                        Serial.println(Smtp.sendTls(host, 465, user, password, from, to, subject, body));
                """, "import io.github.jabrena.juno.hardware.net.email.Smtp; import io.github.jabrena.juno.hardware.serial.Serial;");

        // Then
        assertThat(result.assembly()).contains("juno_smtp_send_tls");
        assertThat(result.runtimeShim()).contains("juno_smtp_send_tls(");
    }

    @Test
    @DisplayName("Rejects an Smtp call that mixes literal and runtime String arguments")
    void shouldRejectMixedLiteralAndRuntimeSmtpStrings() {
        // When & Then
        assertThatThrownBy(() -> compile("demo.NanoMixedMail", """
                        String host = new StringBuilder().append("smtp.example.com").toString();
                        Serial.println(Smtp.sendTls(host, 465, "user", "secret", "from@example.com", "to@example.com",
                                "subject", "body"));
                """, "import io.github.jabrena.juno.hardware.net.email.Smtp; import io.github.jabrena.juno.hardware.serial.Serial;"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("mixes literal and runtime String arguments");
    }

    @Test
    @DisplayName("Lowers Smtp.send's STARTTLS upgrade through the ESP_SSLClient wrapper on the ESP32 core")
    void shouldLowerStarttlsOnTheEsp32Core() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoStarttls", """
                        Serial.println(Smtp.send("smtp.example.com", 587, "user", "secret", "from@example.com",
                                "to@example.com", "subject", "body"));
                """, "import io.github.jabrena.juno.hardware.net.email.Smtp; import io.github.jabrena.juno.hardware.serial.Serial;");

        // Then
        assertThat(result.runtimeShim()).contains("#include <ESP_SSLClient.h>", "juno_smtp_send(",
                "ESP_SSLClient client;", "connectSSL()");
    }

    @Test
    @DisplayName("Lowers Wifi.beginAP through the core's softAP helpers")
    void shouldLowerAccessPointOnTheSoftAp() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoAp", """
                        ArduinoUno.getWifi().beginAP("juno-ap", "secret123");
                """, "import io.github.jabrena.juno.hardware.ArduinoUno;");

        // Then
        assertThat(result.runtimeShim()).contains("juno_wifi_begin_ap(", "WiFi.softAP(ssid, password)");
        assertThat(result.runtimeShim()).doesNotContain("WiFi.beginAP");
    }

    @Test
    @DisplayName("Generates UUIDs from the ESP32 hardware random-number generator")
    void shouldGenerateUuidsOnTheNano() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoUuid", """
                        Serial.println(java.util.UUID.randomUUID().toString());
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // Then
        assertThat(result.runtimeShim()).contains("esp_fill_random(target, length)", "juno_throw_raise(");
    }

    @Test
    @DisplayName("Lowers java.math BigInteger and BigDecimal onto the Nano's strtod-backed shim")
    void shouldLowerBigNumbersOnTheNano() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoBig", """
                        java.math.BigInteger value = java.math.BigInteger.TWO.pow(64);
                        Serial.println(value.toString());
                        Serial.println(new java.math.BigDecimal(2).sqrt(new java.math.MathContext(20)).toString());
                """, "import io.github.jabrena.juno.hardware.serial.Serial;");

        // Then
        assertThat(result.runtimeShim()).contains("juno_big_integer_double_value", "strtod(");
    }

    @Test
    @DisplayName("Runs PoweredUpHubRemote over the ESP32 BLE library through the juno_ble adapter")
    void shouldRunPoweredUpOverTheEsp32Ble() throws Exception {
        // When
        CompilationResult result = compile("demo.NanoHub", """
                        PoweredUpHubRemote hub = new PoweredUpHubRemote();
                        Serial.println(hub.connect(0) ? 1 : 0);
                """, "import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;"
                + " import io.github.jabrena.juno.hardware.serial.Serial;");

        // Then
        assertThat(result.runtimeShim()).contains("#include <BLEDevice.h>", "JunoBLECharacteristic",
                "juno_ble.scanForUuid(");
        assertThat(result.runtimeShim()).doesNotContain("#include <ArduinoBLE.h>");
    }

    @Test
    @DisplayName("Reads a file from the Nano Connector Carrier's microSD slot through the SdFat shim helpers")
    void shouldLowerSdCardAccessOnTheConnectorCarrier() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoSd", """
                        Serial.begin(BaudRate.BAUD_115200);
                        SdCard sdCard = new NanoConnectorCarrier().getSdCard();
                        if (sdCard.begin()) {
                            InputStream file = sdCard.open("app.txt");
                            if (file != null) {
                                try {
                                    while (file.available() > 0) {
                                        Serial.println(file.read());
                                    }
                                    file.close();
                                } catch (java.io.IOException failure) {
                                    Serial.println("read failed");
                                }
                            }
                        }
                """, SERIAL_IMPORTS + """
                import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
                import io.github.jabrena.juno.hardware.device.sd.SdCard;
                import java.io.InputStream;
                """);

        // When & Then
        assertThat(result.assembly())
                .contains("call8 juno_sd_begin\n", "call8 juno_sd_open\n", "call8 juno_sd_file_available\n")
                .contains("call8 juno_sd_file_read\n", "call8 juno_sd_file_close\n");
        assertThat(result.runtimeShim())
                .contains("#include <SPI.h>\n#include <SdFat.h>\n", "static SdFat32 juno_sd;")
                .contains("extern \"C\" int32_t juno_sd_begin(int32_t chipSelectPin) {")
                .contains("juno_sd.begin(juno_nano_pin(chipSelectPin))");
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
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoNanoEsp32Board.class)
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
                        int raw = ArduinoUno.getGpio().analogRead(0);
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
                        double angle = ArduinoUno.getGpio().analogRead(0) / 100.0;
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
    @DisplayName("Allocates and indexes primitive arrays nested deeper than two dimensions")
    void shouldLowerThreeDimensionalArrays() throws Exception {
        // Given
        CompilationResult result = compile("demo.NanoThreeDimensionalArray", """
                        int[][][] values = new int[2][3][4];
                        values[1][2][3] = 42;
                        Serial.println(values[1][2][3]);
                """, SERIAL_IMPORTS);

        // When & Then
        assertThat(result.assembly())
                .contains("movi a10, 12\n", "movi a10, 16\n", "movi a10, 20\n")
                .contains("call8 juno_alloc\n");
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        assemble(gcc, "NanoThreeDimensionalArray", result);
    }

    @Test
    @DisplayName("Enables and refreshes the Nano ESP32 task watchdog")
    void shouldLowerTheEsp32Watchdog() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoWatchdog", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                @Board(ArduinoNanoEsp32Board.class)
                @Watchdog(timeoutMillis = 3001)
                public final class NanoWatchdog {
                    public static void main(String[] args) {
                        for (;;) {
                        }
                    }
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains("call8 juno_watchdog_begin\n", "call8 yield\n");
        assertThat(result.runtimeShim()).contains("#include <esp_task_wdt.h>", "esp_task_wdt_init(4u, true);",
                "esp_task_wdt_add(nullptr);", "esp_task_wdt_reset();", "extern \"C\" void yield()")
                .doesNotContain("#include <WDT.h>");
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
    @DisplayName("Passes argument words beyond the sixth on the stack, below the caller's slots")
    void shouldPassArgumentWordsBeyondTheSixthOnTheStack() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoWide", MANY_ARGUMENTS);

        // When & Then
        assertThat(result.assembly())
                .containsPattern("l32i a8, a1, \\d+\n    s32i a8, a1, 0\n")
                .containsPattern("s32i a8, a1, 8\n    l32i a8, a1, \\d+\n    s32i a8, a1, 12\n")
                .containsPattern("l32i a9, a1, \\d+\n    s32i a9, a1, \\d+\n")
                .doesNotContain("more than 6 argument words");
    }

    @Test
    @DisplayName("A long that no longer fits the registers moves to the stack, leaving a7 unused as GCC does")
    void shouldMoveALongThatOverflowsTheRegistersToTheStack() throws Exception {
        // Given
        CompilationResult result = compileSource("demo.NanoWide", MANY_ARGUMENTS);

        // When & Then
        // mixed(int a, int b, int c, int d, int e, long f, int g, int h): a-e in a2-a6, f at stack words 0-1, g and h
        // at stack words 2-3, as GCC's own callee reads them (sp + frame + 0, 4, 8, 12).
        assertThat(Stream.of(result.assembly().split("\n    \\.type "))
                .anyMatch(function -> readsStackWords(function, 0, 4, 8, 12))).isTrue();
    }

    @Test
    @DisplayName("Calls with stack arguments assemble with the ESP32-S3 toolchain")
    void shouldAssembleStackArgumentsWithTheXtensaToolchain() throws Exception {
        // Given
        String gcc = availableXtensaGcc();
        Assumptions.assumeTrue(gcc != null, "No xtensa-esp32s3-elf-gcc toolchain available");
        CompilationResult result = compileSource("demo.NanoWide", MANY_ARGUMENTS);

        // When & Then
        assemble(gcc, "NanoWide", result);
    }

    /** Whether {@code function} copies incoming stack words at these offsets above its frame into its slots. */
    private boolean readsStackWords(String function, int... offsets) {
        Matcher entry = Pattern.compile("entry a1, (\\d+)\n").matcher(function);
        if (!entry.find()) {
            return false;
        }
        int frameBytes = Integer.parseInt(entry.group(1));
        return IntStream.of(offsets).allMatch(offset -> function.contains("l32i a9, a1, " + (frameBytes + offset) + "\n"));
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
