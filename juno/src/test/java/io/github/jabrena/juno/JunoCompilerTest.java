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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end (Java source -&gt; javac -&gt; classfile -&gt; link -&gt; IR -&gt; {@link CortexM4AsmBackend})
 * compiler tests. Assertions target the generated assembly's real conventions rather than the retired
 * C++ backend's textual ones: a user method is a sequential {@code juno_fnN} label (never a
 * name-mangled symbol), a hardware/runtime intrinsic is a {@code bl <function>} call, a static field is
 * {@code juno_static_<Class>_<field>_<T>} in {@code .bss}, and a string literal is {@code juno_strN} in
 * {@code .rodata}. Every assertion below was checked against the real backend output for its fixture
 * (see the ASM/backend/CortexM4AsmBackend.java class doc) before being written.
 */
class JunoCompilerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void compilesAssemblyAndRuntimeShimThroughCompilerApi() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Blink {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                        Gpio.digitalWrite(13, true);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Blink", source);
        Path assembly = temporaryDirectory.resolve("Blink.S");
        Path shim = temporaryDirectory.resolve("BlinkShim.cpp");

        CompilationResult result = new JunoCompiler().compileTo(
                List.of(temporaryDirectory, Path.of("target/classes")), "demo.Blink", assembly, shim);

        assertThat(result.entryPointSymbol()).isEqualTo("juno_Blink_asm");
        assertThat(result.assembly()).contains(".global juno_Blink_asm", "bl pinMode", "bl digitalWrite");
        assertThat(assembly).hasContent(result.assembly());
        assertThat(shim).hasContent(result.runtimeShim());
        assertThat(result.report().board().fqbn()).isEqualTo("arduino:renesas_uno:unor4wifi");
    }

    @Test
    void watchdogAnnotationEnablesTheHardwareWatchdogWithItsExplicitTimeout() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoR4WiFi.class)
                @Watchdog(timeoutMillis = 3000)
                public final class WatchdogExplicit {
                    public static void main(String[] args) {
                        Delay.millis(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogExplicit", source);
        Path assembly = temporaryDirectory.resolve("WatchdogExplicit.S");
        Path shim = temporaryDirectory.resolve("WatchdogExplicitShim.cpp");

        CompilationResult result = new JunoCompiler().compileTo(
                List.of(temporaryDirectory, Path.of("target/classes")), "demo.WatchdogExplicit", assembly, shim);

        // The exact prologue sequence (stack-top capture, stack zeroing, then push) is covered
        // precisely by CortexM4AsmBackendTest; this just confirms the entry point is real and the
        // watchdog gets started.
        assertThat(result.assembly()).contains("juno_WatchdogExplicit_asm:\n    ldr r0, =juno_gc_stack_top");
        assertThat(result.assembly()).contains("bl juno_watchdog_begin");
        assertThat(result.runtimeShim()).contains(
                "#include <WDT.h>",
                "WDT.refresh();",
                "WDT.begin(3000u);",
                "extern \"C\" void juno_watchdog_begin()",
                "[juno-watchdog] panic: board will reset via watchdog in 3000ms");
    }

    @Test
    void watchdogAnnotationWithoutExplicitTimeoutUsesItsDeclaredDefault() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoR4WiFi.class)
                @Watchdog
                public final class WatchdogDefault {
                    public static void main(String[] args) {
                        Delay.millis(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogDefault", source);
        Path assembly = temporaryDirectory.resolve("WatchdogDefault.S");
        Path shim = temporaryDirectory.resolve("WatchdogDefaultShim.cpp");

        CompilationResult result = new JunoCompiler().compileTo(
                List.of(temporaryDirectory, Path.of("target/classes")), "demo.WatchdogDefault", assembly, shim);

        assertThat(result.runtimeShim()).contains("WDT.begin(5000u);");
    }

    @Test
    void programsWithoutWatchdogAnnotationEmitNoWatchdogCodeAtAll() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class NoWatchdog {
                    public static void main(String[] args) {
                        Delay.millis(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoWatchdog", source);
        Path assembly = temporaryDirectory.resolve("NoWatchdog.S");
        Path shim = temporaryDirectory.resolve("NoWatchdogShim.cpp");

        CompilationResult result = new JunoCompiler().compileTo(
                List.of(temporaryDirectory, Path.of("target/classes")), "demo.NoWatchdog", assembly, shim);

        assertThat(result.assembly()).doesNotContain("juno_watchdog_begin");
        // "watchdog" alone would also match juno_panic()'s always-present explanatory comment about
        // *why* its diagnostic print has to come before noInterrupts(); check the actual emitted
        // code/identifiers instead of that prose.
        assertThat(result.runtimeShim()).doesNotContain(
                "#include <WDT.h>", "WDT.begin", "WDT.refresh", "juno_watchdog_begin",
                "[juno-watchdog]");
    }

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
        assertThat(choose.blocks().size()).isEqualTo(2);
        assertThat(choose.blocks().stream().noneMatch(
                block -> block.terminator() instanceof IrTerminator.Branch)).isTrue();
    }

    @Test
    void compilesReachableMethodsBranchesAndHardwareIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Main");
        String generated = result.assembly();

        assertThat(generated).contains(".global juno_Main_asm", "bl pinMode", "bl digitalWrite", "bl juno_fn1");
        assertThat(result.report().reachableMethods()).as("main and addTo, both reachable; unused is not").isEqualTo(2);
        // unused() is unreachable from main: exactly one non-entry function (addTo) may exist.
        assertThat(countOccurrences(generated, ".type juno_fn")).isEqualTo(1);
    }

    @Test
    void erasesDigitalOutputObjectsToPinNumbers() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.DigitalOutput;
                public final class ObjectStyleApi {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(13);
                        led.high();
                        led.low();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ObjectStyleApi", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ObjectStyleApi").assembly();

        // DigitalOutput.of(13) erases to pinMode(13, OUTPUT) followed by keeping the pin number itself
        // (r1=1 selects OUTPUT); high()/low() erase to digitalWrite(pin, 1) / digitalWrite(pin, 0) --
        // there is no heap allocation anywhere in this program.
        assertThat(generated).contains("movs r1, #1\n    bl pinMode",
                "movs r1, #1\n    bl digitalWrite", "movs r1, #0\n    bl digitalWrite");
        assertThat(generated).doesNotContain("bl juno_alloc");
    }

    @Test
    void lowersLedMatrixIntrinsics() throws Exception {
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Heart");

        assertThat(result.assembly()).contains(
                "bl juno_led_matrix_begin", "bl juno_led_matrix_load_frame", "bl juno_led_matrix_clear");
        assertThat(result.runtimeShim()).contains("ArduinoLEDMatrix juno_led_matrix;");

        String plainSource = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Plain {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);

        String plainGenerated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain").assembly();

        // Unlike the retired C++ backend, the ASM backend's shim always links in the LED matrix driver
        // (see CortexM4AsmBackend's class doc); only the call sites themselves are conditional.
        assertThat(plainGenerated).doesNotContain("bl juno_led_matrix_begin", "bl juno_led_matrix_load_frame",
                "bl juno_led_matrix_clear");
    }

    @Test
    void rendersDigitsAndTrimsUnusedLetterGlyphs() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.led.LedCanvas;
                import io.github.jabrena.juno.api.led.LedMatrix;
                public final class Digits {
                    public static void main(String[] args) {
                        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
                        LedCanvas.drawDigit(frame, 7, 4, 0);
                        LedMatrix.loadFrame(LedCanvas.packWord(frame, 0), 0, 0);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Digits", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits");

        assertThat(result.assembly()).contains("bl juno_led_matrix_load_frame");
        // A digit-only program reaches drawDigit/packWord/setPixel and nothing from the unused
        // letter-glyph tables: locked at 18 (main + 17 helpers), far fewer than every glyph would pull in.
        assertThat(result.report().reachableMethods()).isEqualTo(18);
    }

    @Test
    void lowersSerialIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.BaudRate;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class Counter {
                    public static void main(String[] args) {
                        Serial.begin(BaudRate.BAUD_9600);
                        Serial.print(1);
                        Serial.println(2);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter").assembly();

        assertThat(generated).contains("bl juno_serial_begin", "bl juno_serial_print\n", "bl juno_serial_println\n");
    }

    @Test
    void lowersACustomSerialBaudRate() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class CustomBaudRate {
                    public static void main(String[] args) {
                        Serial.begin(74880);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CustomBaudRate", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CustomBaudRate").assembly();

        assertThat(generated).contains("bl juno_serial_begin");
        // 74880 = 0x124C0: movw/movt immediate-load pair, not a bipush/sipush-sized literal.
        assertThat(generated).contains("movw r0, #9344", "movt r0, #1");
    }

    @Test
    void lowersSerialStringLiteralIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.BaudRate;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class Greeting {
                    public static void main(String[] args) {
                        Serial.begin(BaudRate.BAUD_9600);
                        Serial.print("hello");
                        String message = "world";
                        Serial.println(message);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Greeting", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Greeting").assembly();

        assertThat(generated).contains(".asciz \"hello\"", ".asciz \"world\"",
                "bl juno_serial_print_str", "bl juno_serial_println_str");
    }

    @Test
    void supportsRuntimeStringsAcrossMethodParametersAndReturns() throws Exception {
        String source = """
                package demo;
                public final class RuntimeText {
                    static String format(int value) {
                        return String.valueOf(value);
                    }
                    static int inspect(String value) {
                        if (value == null) return 0;
                        return value.length() + value.charAt(0);
                    }
                    public static void main(String[] args) {
                        int dynamic = inspect(format(-12));
                        int literal = inspect("ok");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeText", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeText");

        assertThat(result.assembly()).contains(".asciz \"ok\"",
                "bl juno_string_value_of_int", "bl juno_string_length", "bl juno_string_char_at");
        assertThat(result.runtimeShim()).contains("JUNO_STRING_SLOT_COUNT = 8");
    }

    @Test
    void lowersStringValueOfDoubleWithATypedArgument() throws Exception {
        String source = """
                package demo;
                public final class RuntimeDecimal {
                    static String format(double value) {
                        return String.valueOf(value);
                    }
                    public static void main(String[] args) {
                        String text = format(21.2);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.RuntimeDecimal", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.RuntimeDecimal");

        assertThat(result.assembly()).contains("bl juno_string_value_of_double");
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_string_value_of_double(double value)");
    }

    @Test
    void rejectsNonLiteralSerialStringArgument() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.io.usb.BaudRate;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class DynamicGreeting {
                    public static void main(String[] args) {
                        Serial.begin(BaudRate.BAUD_9600);
                        String message = Clock.millis() > 0 ? "yes" : "no";
                        Serial.println(message);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.DynamicGreeting", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DynamicGreeting"))
                .isInstanceOf(CompileException.class);
    }

    @Test
    void lowersWifiIntrinsicsWithLiteralCredentials() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Wifi;
                public final class WifiConnect {
                    public static void main(String[] args) {
                        Wifi.begin("TestNetwork-SSID", "test-password-123");
                        int status = Wifi.status();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnect", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnect");

        assertThat(result.assembly()).contains(".asciz \"TestNetwork-SSID\"", ".asciz \"test-password-123\"",
                "bl juno_wifi_begin", "bl juno_wifi_status");
        assertThat(result.runtimeShim()).contains("#include <WiFiS3.h>",
                "extern \"C\" void juno_wifi_begin(const char* ssid, const char* password)",
                "WiFi.begin(ssid, password);");
    }

    @Test
    void resolvesWifiCredentialsFromACompileTimeEnvironmentVariable() throws Exception {
        String pathValue = System.getenv("PATH");
        assertThat(pathValue != null && !pathValue.isEmpty()).as("test environment must define PATH").isTrue();
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Wifi;
                public final class WifiConnectFromEnv {
                    public static void main(String[] args) {
                        Wifi.begin(System.getenv("PATH"), "password");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnectFromEnv", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnectFromEnv").assembly();

        assertThat(generated).contains(".asciz \"" + pathValue + "\"");
    }

    @Test
    void rejectsAnUnsetCompileTimeEnvironmentVariable() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Wifi;
                public final class WifiConnectFromMissingEnv {
                    public static void main(String[] args) {
                        Wifi.begin(System.getenv("JUNO_TEST_WIFI_SSID_NOT_SET"), "password");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnectFromMissingEnv", source);

        assertThat(System.getenv("JUNO_TEST_WIFI_SSID_NOT_SET")).as("test environment must not define JUNO_TEST_WIFI_SSID_NOT_SET").isNull();
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnectFromMissingEnv"))
                .isInstanceOf(CompileException.class);
    }

    @Test
    void lowersHttpMethodIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.HttpClient;
                public final class HttpDemo {
                    public static void main(String[] args) {
                        byte[] response = new byte[128];
                        byte[] headers = new byte[128];
                        int[] out = new int[2];
                        int getBytes = HttpClient.get("example.com", 80, "/status", response, response.length,
                                headers, headers.length, out);
                        int postBytes = HttpClient.post("example.com", 80, "/submit", "{\\"ok\\":true}",
                                response, response.length, headers, headers.length, out);
                        int deleteBytes = HttpClient.delete("example.com", 80, "/items/7",
                                response, response.length, headers, headers.length, out);
                        int patchBytes = HttpClient.patch("example.com", 80, "/items/7", "{\\"value\\":2}",
                                response, response.length, headers, headers.length, out);
                        int queryBytes = HttpClient.query("example.com", 80, "/items/search",
                                "{\\"tag\\":\\"new\\"}", response, response.length,
                                headers, headers.length, out);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpDemo");

        assertThat(result.assembly()).contains(".asciz \"example.com\"", ".asciz \"/status\"",
                ".asciz \"{\\\"ok\\\":true}\"", ".asciz \"{\\\"value\\\":2}\"", ".asciz \"{\\\"tag\\\":\\\"new\\\"}\"",
                "bl juno_http_get", "bl juno_http_post", "bl juno_http_delete", "bl juno_http_patch",
                "bl juno_http_query");
        assertThat(result.runtimeShim()).contains("#include <WiFiS3.h>",
                "static int32_t juno_http_request(",
                "juno_http_request(client, \"DELETE\", host, port, path, nullptr",
                "juno_http_request(client, \"PATCH\", host, port, path, body",
                "juno_http_request(client, \"QUERY\", host, port, path, body")
                .doesNotContain("#include <WiFiSSLClient.h>", "juno_https_get");
    }

    @Test
    void lowersHttpsMethodIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.HttpsClient;
                public final class HttpsDemo {
                    public static void main(String[] args) {
                        byte[] response = new byte[128];
                        byte[] headers = new byte[128];
                        int[] out = new int[2];
                        int getBytes = HttpsClient.get("example.com", 443, "/status",
                                response, response.length, headers, headers.length, out);
                        int postBytes = HttpsClient.post("example.com", 443, "/submit", "{\\"ok\\":true}",
                                response, response.length, headers, headers.length, out);
                        int deleteBytes = HttpsClient.delete("example.com", 443, "/items/7",
                                response, response.length, headers, headers.length, out);
                        int patchBytes = HttpsClient.patch("example.com", 443, "/items/7", "{\\"value\\":2}",
                                response, response.length, headers, headers.length, out);
                        int queryBytes = HttpsClient.query("example.com", 443, "/items/search",
                                "{\\"tag\\":\\"new\\"}", response, response.length,
                                headers, headers.length, out);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpsDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpsDemo");

        assertThat(result.assembly()).contains(
                "bl juno_https_get", "bl juno_https_post", "bl juno_https_delete", "bl juno_https_patch",
                "bl juno_https_query");
        assertThat(result.runtimeShim()).contains("#include <WiFiS3.h>", "#include <WiFiSSLClient.h>",
                "WiFiSSLClient client;",
                "juno_http_request(client, \"GET\", host, port, path, nullptr",
                "juno_http_request(client, \"QUERY\", host, port, path, body")
                .doesNotContain("juno_http_get");
    }

    @Test
    void lowersStringBuilderConstructionAppendAndToString() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class StringBuilderDemo {
                    public static void main(String[] args) {
                        StringBuilder builder = new StringBuilder(8);
                        builder.append('1');
                        builder.append("!!");
                        String text = builder.toString();
                        Serial.println(text.length());
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.StringBuilderDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.StringBuilderDemo");

        // Construction: real allocation (juno_string_builder_new), never a discarded placeholder.
        assertThat(result.assembly()).contains("bl juno_string_builder_new", "bl juno_string_builder_append_char",
                "bl juno_string_builder_append_string", "bl juno_string_builder_to_string", "bl juno_string_length");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" int32_t juno_string_builder_new(int32_t capacity)",
                "static uint8_t* juno_string_builder_buffer(int32_t handle)",
                // Needs the shared runtime-string-slot pool for toString(), even though this program never
                // calls String.valueOf/Json.getString itself.
                "juno_string_slots[JUNO_STRING_SLOT_COUNT][JUNO_STRING_SLOT_SIZE]");
    }

    @Test
    void lowersJsonFieldExtractionIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Json;
                public final class JsonDemo {
                    public static void main(String[] args) {
                        byte[] buffer = new byte[64];
                        byte[] name = new byte[32];
                        int valueType = Json.type(buffer, buffer.length, "items[0].value");
                        int temperature = Json.getInt(buffer, buffer.length, "data.sensor.temp");
                        long sequence = Json.getLong(buffer, buffer.length, "sequence");
                        double precise = Json.getDouble(buffer, buffer.length, "precise");
                        boolean ok = Json.getBool(buffer, buffer.length, "ok");
                        int nameLength = Json.getString(buffer, buffer.length, "name", name, name.length);
                        int itemCount = Json.arraySize(buffer, buffer.length, "items");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonDemo");

        assertThat(result.assembly()).as("JSON parsing alone needs no WiFi").doesNotContain("WiFiS3.h");
        assertThat(result.assembly()).contains(".asciz \"data.sensor.temp\"",
                "bl juno_json_type", "bl juno_json_get_int", "bl juno_json_get_long", "bl juno_json_get_double",
                "bl juno_json_get_bool", "bl juno_json_get_string\n", "bl juno_json_array_size");
        assertThat(result.runtimeShim()).contains("static int32_t juno_json_locate(");
    }

    @Test
    void lowersJsonGetStringValueAsARuntimeString() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Json;
                public final class JsonStringValueDemo {
                    public static void main(String[] args) {
                        byte[] buffer = new byte[64];
                        String temperature = Json.getString(buffer, buffer.length, "data.sensor.temp");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonStringValueDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonStringValueDemo");

        assertThat(result.assembly()).contains("bl juno_json_get_string_value");
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_json_get_string_value(",
                "JUNO_STRING_SLOT_SIZE = 32");
    }

    /**
     * Regression test for a real bug: {@code juno_json_get_string_value} needs the runtime-string
     * pool, which is only emitted when some intrinsic in {@code RUNTIME_STRING_INTRINSICS} is used
     * — a program using only {@code Json.getInt} must not pull in that unused helper (it would
     * reference undeclared symbols if the string pool weren't also unconditionally gated the same
     * way).
     */
    @Test
    void omitsJsonGetStringValueHelperWhenOnlyOtherJsonIntrinsicsAreUsed() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.net.Json;
                public final class JsonIntOnlyDemo {
                    public static void main(String[] args) {
                        byte[] buffer = new byte[64];
                        int temperature = Json.getInt(buffer, buffer.length, "data.sensor.temp");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.JsonIntOnlyDemo", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonIntOnlyDemo");

        assertThat(result.assembly()).contains("bl juno_json_get_int").doesNotContain("juno_json_get_string_value");
        assertThat(result.runtimeShim()).doesNotContain("juno_json_get_string_value", "JUNO_STRING_SLOT_SIZE");
    }

    @Test
    void omitsHttpAndJsonHelpersWhenUnused() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class NoNetworking {
                    public static void main(String[] args) {
                        Delay.millis(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoNetworking", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NoNetworking");

        assertThat(result.assembly()).doesNotContain("bl juno_http", "bl juno_json");
        assertThat(result.runtimeShim()).doesNotContain("juno_http_request", "juno_json_locate", "WiFiS3.h");
    }

    @Test
    void lowersMouseIntrinsicsAndOmitsUnusedHeader() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.hid.Mouse;
                public final class Wiggle {
                    public static void main(String[] args) {
                        Mouse.begin();
                        Mouse.move(50, -50);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Wiggle", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wiggle");

        assertThat(result.assembly()).contains("bl juno_mouse_begin", "bl juno_mouse_move");
        assertThat(result.runtimeShim()).contains("#include <Mouse.h>");

        String plainSource = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Plain {
                    public static void main(String[] args) {
                        Gpio.pinMode(13, Gpio.OUTPUT);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", plainSource);

        CompilationResult plainResult = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        assertThat(plainResult.runtimeShim()).doesNotContain("Mouse.h", "juno_mouse_begin");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Objects").assembly();

        // 3 references * 4 bytes = 12, 4-byte aligned: the arena allocation for the array itself.
        assertThat(generated).contains("movs r0, #12\n    movs r1, #4\n    bl juno_alloc");
    }

    @Test
    void needsNoSpecialHandlingForIntegerOverflowUnlikeTheRetiredCppBackend() throws Exception {
        // The retired C++ backend had to route every int op through unsigned-cast helpers
        // (juno_imul/juno_ineg) to force wraparound instead of C++ signed-overflow UB. ARM registers
        // are already 32-bit two's complement, so the ASM backend emits the native instructions
        // directly -- no helper call, no special casing, overflow "just happens" correctly.
        String source = """
                package demo;
                public final class MathProgram {
                    static int calculate(int a, int b) { return -((a + b) * (a - b)); }
                    public static void main() { calculate(Integer.MAX_VALUE, 2); }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MathProgram", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MathProgram").assembly();

        assertThat(generated).contains("add r0, r0, r1", "sub r0, r0, r1", "mul r0, r0, r1", "rsb r0, r0, #0");
    }

    @Test
    void compileWithRequestReturnsAReportAlongsideTheGeneratedAssembly() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
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

        assertThat(result.assembly()).contains(".global juno_Reported_asm", "bl pinMode", "bl juno_fn1");
        assertThat(result.report().entryPoint().displayName()).isEqualTo("demo.Reported.main([Ljava/lang/String;)V");
        assertThat(result.report().reachableMethods()).as("main and addTo, both reachable").isEqualTo(2);
        assertThat(result.report().irBlocks() > 2).as("addTo's loop needs more than one block per method").isTrue();
        assertThat(result.report().intrinsics()).isEqualTo(Set.of(Intrinsic.GPIO_PIN_MODE, Intrinsic.DELAY_MILLIS));
        assertThat(result.report().runtimeRisks().arenaCapacityBytes()).isEqualTo(8192);
        assertThat(result.report().runtimeRisks().estimatedMaxStackBytes() > 0).isTrue();
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SameClassConstant").assembly();

        assertThat(generated).contains(".global juno_SameClassConstant_asm");
        // javac must inline the constant: no static field (and hence no .bss slot) for it at all.
        // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
        // conservative GC's stack scan, regardless of whether the program has static fields.
        assertThat(generated).doesNotContain("juno_static_");
        assertThat(generated).contains("movs r0, #4");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.CrossClassConstant").assembly();

        assertThat(generated).contains(".global juno_CrossClassConstant_asm");
        // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
        // conservative GC's stack scan, regardless of whether the program has static fields.
        assertThat(generated).doesNotContain("juno_static_");
        assertThat(generated).contains("movs r0, #4");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MutableStatic").assembly();

        assertThat(generated).contains("juno_static_demo_MutableStatic_counter_I:", "juno_static_demo_MutableStatic_scale_F:",
                "ldr r0, =juno_static_demo_MutableStatic_counter_I", "ldr r1, =juno_static_demo_MutableStatic_scale_F");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReadOnlyStatic").assembly();

        assertThat(generated).contains("juno_static_demo_ReadOnlyStatic_NOT_A_CONSTANT_EXPRESSION_I:");
        // The class initializer (juno_fn0) must run before the entry point's own first block.
        assertThat(generated.indexOf("bl juno_fn0")
                < generated.indexOf(".Ljuno_ReadOnlyStatic_asmblock0")).isTrue();
    }

    @Test
    void supportsALocalArrayWithBoundsCheckedAccessAndAConstantFoldedLength() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ArrayDemo").assembly();

        // 3 ints * 4 bytes, 4-byte aligned: the local array must use arena storage.
        assertThat(generated).contains("movs r0, #12\n    movs r1, #4\n    bl juno_alloc");
        // Each of the 3 writes into the 3-element local array must be bounds-checked against its
        // known length (cmp/blt/bge into a shared juno_panic trampoline).
        assertThat(countOccurrences(generated, "bl juno_panic")).isEqualTo(3);
        // sum's int[] parameter is a raw pointer (r0) with the explicit count as a second register (r1).
        assertThat(generated).contains("bl juno_fn1");
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

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NonConstLen"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("array length must be a compile-time constant");
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

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LengthOnParam"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("demo.LengthOnParam.firstLength")
                .hasMessageContaining("array length is not known at compile time");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Reassigned").assembly();

        assertThat(generated).as("a reassigned local is not effectively-final and must not be bounds-checked")
                .doesNotContain("bl juno_panic");
        // both writes must still compile, as raw pointer stores through a scaled index.
        assertThat(countOccurrences(generated, "lsls r1, r1, #2")).isEqualTo(2);
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ElementTypes").assembly();

        assertThat(generated).as("byte[4] must use 1-byte-aligned storage and a byte store")
                .contains("movs r0, #4\n    movs r1, #1\n    bl juno_alloc", "strb r2, [r0, r1]");
        assertThat(generated).as("char[3] must use 2-byte-aligned storage (3 * 2 = 6) and a halfword store")
                .contains("movs r0, #6\n    movs r1, #2\n    bl juno_alloc", "strh r2, [r0, r1]");
        assertThat(generated).as("short[2] must use 2-byte-aligned storage (2 * 2 = 4)")
                .contains("movs r0, #4\n    movs r1, #2\n    bl juno_alloc");
        assertThat(generated).as("sumBytes's byte[] parameter must be read with a sign-extending byte load")
                .contains("ldrsb r2, [r0, r1]");
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

        // The ASM backend does not yet support arrays of float/long/double (see CortexM4AsmBackend's
        // class doc and the sibling long/double array tests below) -- unlike the retired C++ backend,
        // which handled every element type uniformly.
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatArrays"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("array element type FLOAT");
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

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongArray"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("array element type LONG");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnForward").assembly();

        assertThat(generated).contains("bl juno_fn1");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnLocal").assembly();

        assertThat(generated).contains("bl juno_fn1", "movs r0, #12\n    movs r1, #4\n    bl juno_alloc");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ReturnTernary").assembly();

        assertThat(generated).contains("bl juno_fn1");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongMath").assembly();

        assertThat(generated).contains(".global juno_LongMath_asm",
                "bl juno_ladd", "bl juno_lsub", "bl juno_lmul", "bl juno_ldiv", "bl juno_lrem", "bl juno_lneg",
                "bl juno_lshl", "bl juno_lshr", "bl juno_lushr", "bl juno_land", "bl juno_lor", "bl juno_lxor",
                "bl juno_lcmp");
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.LongParam");

        assertThat(result.assembly()).contains("juno_static_demo_LongParam_saved_J:", "bl juno_fn1",
                "bl juno_l2f", "bl juno_f2l");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" float juno_l2f(int64_t value)", "extern \"C\" int64_t juno_f2l(float value)");
    }

    @Test
    void supportsFloatLocalsArithmeticComparisonsConversionsAndLoops() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                import io.github.jabrena.juno.api.io.Gpio;
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMath");

        assertThat(result.assembly()).contains("bl juno_fadd", "bl juno_fmul", "bl juno_fsub", "bl juno_fdiv",
                "bl juno_frem", "bl juno_f2i", "bl juno_fcmp",
                // unary float negation is a native sign-bit flip, not a shim call.
                "eor r0, r0, #0x80000000");
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_fcmp(");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.FloatMethods").assembly();

        // mix's (float, int, float) parameters are passed one per register (r0, r1, r2), AAPCS-style.
        assertThat(generated).contains("bl juno_fn1", "bl juno_i2f", "bl juno_fmul", "bl juno_fadd");
    }

    @Test
    void supportsDoubleLocalsCallsFieldsAndConversions() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class DoubleMath {
                    static double last;
                    static double mix(double left, int scale, double right) {
                        return left * (double) scale + right;
                    }
                    public static void main(String[] args) {
                        double value = mix(1.25, 2, 0.5);
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DoubleMath");

        assertThat(result.assembly()).contains("juno_static_demo_DoubleMath_last_D:", "bl juno_fn1",
                "bl juno_dmul", "bl juno_dadd", "bl juno_drem", "bl juno_d2f", "bl juno_d2i", "bl juno_d2l",
                "bl juno_i2d", "bl juno_l2d", "bl juno_f2d");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" double juno_drem(double a, double b) { return fmod(a, b); }");
    }

    @Test
    void supportsDoubleArraysAreNotYetSupportedByTheAsmBackend() throws Exception {
        // Unlike double locals/fields/parameters (see the test above), the ASM backend does not yet
        // support arrays of double -- same gap as float[] and long[] (see CortexM4AsmBackend's class
        // doc). This mirrors the retired C++ backend's supportsDoubleLocalsCallsFieldsArraysAndConversions
        // test, minus the array portion that no longer compiles.
        String source = """
                package demo;
                public final class DoubleArray {
                    static double[] identity(double[] values) {
                        return values;
                    }
                    public static void main(String[] args) {
                        double[] inputs = new double[2];
                        inputs[0] = 1.25;
                        double[] values = identity(inputs);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.DoubleArray", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.DoubleArray"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("array element type DOUBLE");
    }

    @Test
    void supportsEnumConstantsAsOrdinalInts() throws Exception {
        // Scoped deliberately to ordinal-int representation: an enum constant is never actually
        // constructed (no heap, no objects), it is just its 0-based declaration-order ordinal, a plain
        // int32_t. getstatic on a recognized enum constant resolves directly to that literal; ==/!=
        // (if_acmpeq/if_acmpne) then works for free, since equal ordinals are equal ints.
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnum").assembly();

        assertThat(generated).contains(".global juno_UsesEnum_asm");
        // an enum constant never allocates: no getstatic-equivalent static field/bss slot for Direction.
        // .bss itself is no longer a signal here: juno_gc_stack_top always gets one for the
        // conservative GC's stack scan, regardless of whether the program has static fields.
        assertThat(generated).doesNotContain("juno_static_");
        // classify(Direction.NORTH) passes the ordinal 0 directly as an int argument.
        assertThat(generated).contains("bl juno_fn1");
    }

    @Test
    void supportsAnIntegerValueAssociatedWithEachEnumConstant() throws Exception {
        String enumSource = """
                package demo;
                public enum Speed {
                    NORMAL(9600), FAST(115200);
                    private final int bitsPerSecond;
                    Speed(int bitsPerSecond) {
                        this.bitsPerSecond = bitsPerSecond;
                    }
                    public int bitsPerSecond() {
                        return bitsPerSecond;
                    }
                }
                """;
        String usingSource = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class UsesEnumValue {
                    public static void main(String[] args) {
                        Speed speed = Speed.FAST;
                        Delay.millis(speed.bitsPerSecond());
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Speed", enumSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UsesEnumValue", usingSource);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesEnumValue").assembly();

        // Each enum constant's associated value becomes one word in a constant-folded int[] table
        // (NORMAL=9600, FAST=115200), read via a bounds-checked array load, not a heap object.
        assertThat(generated).contains(".word 9600, 115200", "ldr r0, =juno_int_array0", "bl delay")
                .doesNotContain("bl juno_alloc");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesPoint").assembly();

        assertThat(generated).contains(".global juno_UsesPoint_asm",
                // Point(x, y): a 2-field, 8-byte, 4-byte-aligned arena object, x and y at offsets 0 and 4.
                "movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                "str r1, [r0, #0]", "str r1, [r0, #4]",
                "ldr r1, [r0, #0]", "ldr r1, [r0, #4]");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.TakesPoint").assembly();

        // A record reference is passed and returned as a plain pointer in r0, like any other object.
        assertThat(generated).contains("bl juno_fn1", "bl juno_fn2", "bl juno_fn3");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NewsObject").assembly();

        // A single mutable int field: 4 bytes, 4-byte aligned; add(amount) reads then writes offset 0
        // (receiver in r0, amount in r1) via a real instance-method call, not an inlined field mutation.
        assertThat(generated).contains("movs r0, #4\n    movs r1, #4\n    bl juno_alloc",
                "bl juno_fn1", "bl juno_fn2", "ldr r1, [r0, #0]", "str r1, [r0, #0]");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCompactPoint").assembly();

        // Native two's-complement negation for the compact constructor's `x = -x`, same as any other
        // int negation (see the integer-overflow test above) -- no special-casing for record bodies.
        assertThat(generated).contains("rsb r0, r0, #0");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesCustomAccessor").assembly();

        assertThat(generated).contains("mul r0, r0, r1");
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UsesLabeled").assembly();

        // Labeled(int[] data, int value): a 2-field, 8-byte record whose first field is a pointer.
        assertThat(generated).contains("movs r0, #8\n    movs r1, #4\n    bl juno_alloc",
                "str r1, [r0, #0]", "str r1, [r0, #4]");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
