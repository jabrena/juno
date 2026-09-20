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
        assertThat(choose.blocks().size()).isEqualTo(2);
        assertThat(choose.blocks().stream().noneMatch(
                block -> block.terminator() instanceof IrTerminator.Branch)).isTrue();
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

        assertThat(generated.contains("Closed-world entry point: demo.Main.main")).isTrue();
        assertThat(generated.contains("pinMode(call_arg0, call_arg1)")).isTrue();
        assertThat(generated.contains("digitalWrite(call_arg0, call_arg1 ? HIGH : LOW)")).isTrue();
        assertThat(generated.contains("juno_demo_Main_addTo")).isTrue();
        assertThat(generated.contains("juno_demo_Main_unused")).isFalse();
        assertThat(generated.contains("goto juno_pc_")).isTrue();
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

        assertThat(generated.contains("= juno_digital_output_of(call_arg0);")).isTrue();
        assertThat(generated.contains("pinMode(pin, OUTPUT)")).isTrue();
        assertThat(generated.contains("digitalWrite(call_receiver, HIGH)")).isTrue();
        assertThat(generated.contains("digitalWrite(call_receiver, LOW)")).isTrue();
        assertThat(generated.contains("new DigitalOutput")).isFalse();
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

        assertThat(generated.contains("#include \"Arduino_LED_Matrix.h\"")).isTrue();
        assertThat(generated.contains("ArduinoLEDMatrix juno_led_matrix;")).isTrue();
        assertThat(generated.contains("juno_led_matrix_begin()")).isTrue();
        assertThat(generated.contains("juno_led_matrix_load_frame(call_arg0, call_arg1, call_arg2)")).isTrue();
        assertThat(generated.contains("juno_led_matrix_clear()")).isTrue();

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

        assertThat(plainGenerated.contains("Arduino_LED_Matrix.h")).isFalse();
        assertThat(plainGenerated.contains("ArduinoLEDMatrix")).isFalse();
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Digits");

        assertThat(generated.contains("juno_io_github_jabrena_juno_api_led_LedCanvas_drawDigit")).isTrue();
        assertThat(generated.contains("juno_io_github_jabrena_juno_api_led_LedCanvas_setPixel")).isTrue();
        assertThat(generated.contains("letterARowBits")).isFalse();
        assertThat(generated.contains("LedMatrixFont_letterPixel")).isFalse();
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

        assertThat(generated.contains("Serial.begin(static_cast<unsigned long>(call_arg0))")).isTrue();
        assertThat(generated.contains("Serial.print(call_arg0)")).isTrue();
        assertThat(generated.contains("Serial.println(call_arg0)")).isTrue();
    }

    @Test
    void lowersSerialStringLiteralIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Serial;
                public final class Greeting {
                    public static void main(String[] args) {
                        Serial.begin(9600);
                        Serial.print("hello");
                        String message = "world";
                        Serial.println(message);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Greeting", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Greeting");

        assertThat(generated.contains("const char* call_str0 = \"hello\";")).isTrue();
        assertThat(generated.contains("Serial.print(call_str0)")).isTrue();
        assertThat(generated.contains("const char* call_str0 = \"world\";")).isTrue();
        assertThat(generated.contains("Serial.println(call_str0)")).isTrue();
    }

    @Test
    void rejectsNonLiteralSerialStringArgument() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Clock;
                import io.github.jabrena.juno.api.Serial;
                public final class DynamicGreeting {
                    public static void main(String[] args) {
                        Serial.begin(9600);
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
                import io.github.jabrena.juno.api.net.Wifi;
                public final class WifiConnect {
                    public static void main(String[] args) {
                        Wifi.begin("TestNetwork-SSID", "test-password-123");
                        int status = Wifi.status();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnect", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnect");

        assertThat(generated.contains("#include <WiFiS3.h>")).isTrue();
        assertThat(generated.contains("const char* call_str0 = \"TestNetwork-SSID\";")).isTrue();
        assertThat(generated.contains("const char* call_str1 = \"test-password-123\";")).isTrue();
        assertThat(generated.contains("WiFi.begin(call_str0, call_str1)")).isTrue();
        assertThat(generated.contains("WiFi.status()")).isTrue();
    }

    @Test
    void resolvesWifiCredentialsFromACompileTimeEnvironmentVariable() throws Exception {
        String pathValue = System.getenv("PATH");
        assertThat(pathValue != null && !pathValue.isEmpty()).as("test environment must define PATH").isTrue();
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Wifi;
                public final class WifiConnectFromEnv {
                    public static void main(String[] args) {
                        Wifi.begin(System.getenv("PATH"), "password");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiConnectFromEnv", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WifiConnectFromEnv");

        assertThat(generated.contains("const char* call_str0 = \"" + pathValue.replace("\\", "\\\\") + "\";")).isTrue();
    }

    @Test
    void rejectsAnUnsetCompileTimeEnvironmentVariable() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Wifi;
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
    void rejectsWifiUsageOnTheMinimaWhichHasNoOnboardModule() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.net.Wifi;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaWithWifi {
                    public static void main(String[] args) {
                        Wifi.begin("network", "password");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaWithWifi", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MinimaWithWifi"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Wifi")
                .hasMessageContaining("Minima");
    }

    @Test
    void lowersHttpMethodIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.HttpClient;
                public final class HttpDemo {
                    public static void main(String[] args) {
                        byte[] response = new byte[128];
                        int getBytes = HttpClient.get("example.com", 80, "/status", response, response.length);
                        int postBytes = HttpClient.post("example.com", 80, "/submit", "{\\"ok\\":true}",
                                response, response.length);
                        int deleteBytes = HttpClient.delete("example.com", 80, "/items/7",
                                response, response.length);
                        int patchBytes = HttpClient.patch("example.com", 80, "/items/7", "{\\"value\\":2}",
                                response, response.length);
                        int queryBytes = HttpClient.query("example.com", 80, "/items/search",
                                "{\\"tag\\":\\"new\\"}", response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpDemo", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpDemo");

        assertThat(generated.contains("#include <WiFiS3.h>")).isTrue();
        assertThat(generated.contains("juno_http_get(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_http_post(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_http_delete(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_http_patch(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_http_query(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("const char* call_str0 = \"example.com\";")).isTrue();
        assertThat(generated.contains("const char* call_str1 = \"/status\";")).isTrue();
        assertThat(generated.contains("\"{\\\"ok\\\":true}\"")).isTrue();
        assertThat(generated.contains("\"{\\\"value\\\":2}\"")).isTrue();
        assertThat(generated.contains("\"{\\\"tag\\\":\\\"new\\\"}\"")).isTrue();
        assertThat(generated.contains("static int32_t juno_http_request(")).isTrue();
        assertThat(generated.contains("juno_http_request(client, \"DELETE\", host, port, path, nullptr")).isTrue();
        assertThat(generated.contains("juno_http_request(client, \"PATCH\", host, port, path, body")).isTrue();
        assertThat(generated.contains("juno_http_request(client, \"QUERY\", host, port, path, body")).isTrue();
        assertThat(generated.contains("#include <WiFiSSLClient.h>")).isFalse();
        assertThat(generated.contains("juno_https_get")).isFalse();
    }

    @Test
    void lowersHttpsMethodIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.HttpsClient;
                public final class HttpsDemo {
                    public static void main(String[] args) {
                        byte[] response = new byte[128];
                        int getBytes = HttpsClient.get("example.com", 443, "/status",
                                response, response.length);
                        int postBytes = HttpsClient.post("example.com", 443, "/submit", "{\\\"ok\\\":true}",
                                response, response.length);
                        int deleteBytes = HttpsClient.delete("example.com", 443, "/items/7",
                                response, response.length);
                        int patchBytes = HttpsClient.patch("example.com", 443, "/items/7", "{\\\"value\\\":2}",
                                response, response.length);
                        int queryBytes = HttpsClient.query("example.com", 443, "/items/search",
                                "{\\\"tag\\\":\\\"new\\\"}", response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.HttpsDemo", source);

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.HttpsDemo");

        assertThat(generated.contains("#include <WiFiS3.h>")).isTrue();
        assertThat(generated.contains("#include <WiFiSSLClient.h>")).isTrue();
        assertThat(generated.contains("juno_https_get(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_https_post(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_https_delete(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_https_patch(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("juno_https_query(call_str0, call_arg")).isTrue();
        assertThat(generated.contains("WiFiSSLClient client;")).isTrue();
        assertThat(generated.contains("juno_http_request(client, \"GET\", host, port, path, nullptr")).isTrue();
        assertThat(generated.contains("juno_http_request(client, \"QUERY\", host, port, path, body")).isTrue();
        assertThat(generated.contains("juno_http_get")).isFalse();
    }

    @Test
    void rejectsHttpUsageOnTheMinimaWhichHasNoOnboardModule() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.net.HttpClient;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaWithHttp {
                    public static void main(String[] args) {
                        byte[] response = new byte[16];
                        HttpClient.get("example.com", 80, "/", response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaWithHttp", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MinimaWithHttp"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Minima");
    }

    @Test
    void rejectsQueryUsageOnTheMinimaWhichHasNoOnboardModule() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.net.HttpClient;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaWithQuery {
                    public static void main(String[] args) {
                        byte[] response = new byte[16];
                        HttpClient.query("example.com", 80, "/search", "{}",
                                response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaWithQuery", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MinimaWithQuery"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Minima");
    }

    @Test
    void rejectsHttpsUsageOnTheMinimaWhichHasNoOnboardModule() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.ArduinoUnoR4Minima;
                import io.github.jabrena.juno.api.Board;
                import io.github.jabrena.juno.api.net.HttpsClient;
                @Board(ArduinoUnoR4Minima.class)
                public final class MinimaWithHttps {
                    public static void main(String[] args) {
                        byte[] response = new byte[16];
                        HttpsClient.get("example.com", 443, "/", response, response.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MinimaWithHttps", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MinimaWithHttps"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Minima");
    }

    @Test
    void lowersJsonFieldExtractionIntrinsics() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.net.Json;
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.JsonDemo");

        assertThat(generated.contains("#include <WiFiS3.h>")).as("JSON parsing alone needs no WiFi").isFalse();
        assertThat(generated.contains("juno_json_type(")).isTrue();
        assertThat(generated.contains("juno_json_get_int(")).isTrue();
        assertThat(generated.contains("juno_json_get_long(")).isTrue();
        assertThat(generated.contains("juno_json_get_double(")).isTrue();
        assertThat(generated.contains("juno_json_get_bool(")).isTrue();
        assertThat(generated.contains("juno_json_get_string(")).isTrue();
        assertThat(generated.contains("juno_json_array_size(")).isTrue();
        assertThat(generated.contains("const char* call_str0 = \"data.sensor.temp\";")).isTrue();
        assertThat(generated.contains("static int32_t juno_json_locate(")).isTrue();
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NoNetworking");

        assertThat(generated.contains("juno_http_request")).isFalse();
        assertThat(generated.contains("juno_json_locate")).isFalse();
        assertThat(generated.contains("#include <WiFiS3.h>")).isFalse();
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

        assertThat(generated.contains("#include <Mouse.h>")).isTrue();
        assertThat(generated.contains("Mouse.begin()")).isTrue();
        assertThat(generated.contains(
                "Mouse.move(static_cast<signed char>(call_arg0), static_cast<signed char>(call_arg1))")).isTrue();

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

        assertThat(plainGenerated.contains("Mouse.h")).isFalse();
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

        assertThat(generated.contains("sizeof(int32_t) * (3)")).isTrue();
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

        assertThat(generated.contains("static_cast<uint32_t>(a) + static_cast<uint32_t>(b)")).isTrue();
        assertThat(generated.contains("juno_imul(v")).isTrue();
        assertThat(generated.contains("juno_ineg(v")).isTrue();
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

        assertThat(result.generatedSource().contains("Closed-world entry point: demo.Reported.main")).isTrue();
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SameClassConstant");

        assertThat(generated.contains("Closed-world entry point: demo.SameClassConstant.main")).isTrue();
        assertThat(generated.contains("getstatic")).as("javac must inline the constant, not emit a field read").isFalse();
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

        assertThat(generated.contains("Closed-world entry point: demo.CrossClassConstant.main")).isTrue();
        assertThat(generated.contains("getstatic")).isFalse();
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

        assertThat(generated.contains("static int32_t juno_field_demo_MutableStatic_counter_")).isTrue();
        assertThat(generated.contains("static float juno_field_demo_MutableStatic_scale_")).isTrue();
        assertThat(generated.contains("juno_field_demo_MutableStatic_counter_")).isTrue();
        assertThat(generated.contains("juno_field_demo_MutableStatic_scale_")).isTrue();
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

        assertThat(generated.contains("juno_demo_ReadOnlyStatic__clinit_")).isTrue();
        assertThat(generated.indexOf("juno_demo_ReadOnlyStatic__clinit_")
                < generated.lastIndexOf("juno_demo_ReadOnlyStatic_main_")).isTrue();
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

        assertThat(generated.contains("sizeof(int32_t) * (3)")).as("the local array must use arena storage").isTrue();
        assertThat(generated.contains(">= 3) juno_panic()")).as("writes into the 3-element local array must be bounds-checked against its known length").isTrue();
        assertThat(generated.contains("(int32_t* arg0, int32_t arg1)")).as("sum's int[] parameter must be a pointer, with the explicit count as a second parameter").isTrue();
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

        String generated = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Reassigned");

        assertThat(generated.contains(">= 2) juno_panic()") || generated.contains(">= 3) juno_panic()")).as("a reassigned local is not effectively-final and must not be bounds-checked").isFalse();
        assertThat(generated.contains("] = v")).as("both stores must still compile, as raw pointer writes").isTrue();
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

        assertThat(generated.contains("sizeof(int8_t) * (4)")).as("byte[] must be stored as int8_t, not int32_t").isTrue();
        assertThat(generated.contains("sizeof(uint16_t) * (3)")).as("char[] must be stored as uint16_t").isTrue();
        assertThat(generated.contains("sizeof(int16_t) * (2)")).as("short[] must be stored as int16_t").isTrue();
        assertThat(generated.contains("(int8_t* arg0, int32_t arg1)")).as("sumBytes's byte[] parameter must be an int8_t pointer").isTrue();
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

        assertThat(generated.contains("sizeof(float) * (3)")).as("float[] must use native float storage").isTrue();
        assertThat(generated.contains("(float* arg0, int32_t arg1)")).isTrue();
        assertThat(generated.contains("static float* juno_demo_FloatArrays_identity_")).isTrue();
        assertThat(generated.contains("return reinterpret_cast<float*>(v")).isTrue();
        assertThat(generated.contains(">= 3) juno_panic()")).isTrue();
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

        assertThat(generated.contains("static int32_t* juno_demo_ReturnForward_pick_")).as("an int[]-returning method must have a pointer return type").isTrue();
        assertThat(generated.contains("return reinterpret_cast<int32_t*>(v")).isTrue();
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

        assertThat(generated.contains("static int32_t* juno_demo_ReturnLocal_makeArray_")).isTrue();
        assertThat(generated.contains("sizeof(int32_t) * (3)")).isTrue();
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

        assertThat(generated.contains("static int32_t* juno_demo_ReturnTernary_pick_")).isTrue();
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

        assertThat(generated.contains("Closed-world entry point: demo.LongMath.main")).isTrue();
        assertThat(generated.contains("juno_ladd(")).isTrue();
        assertThat(generated.contains("juno_lsub(")).isTrue();
        assertThat(generated.contains("juno_lmul(")).isTrue();
        assertThat(generated.contains("juno_ldiv(")).isTrue();
        assertThat(generated.contains("juno_lrem(")).isTrue();
        assertThat(generated.contains("juno_lneg(")).isTrue();
        assertThat(generated.contains("juno_lshl(")).isTrue();
        assertThat(generated.contains("juno_lshr(")).isTrue();
        assertThat(generated.contains("juno_lushr(")).isTrue();
        assertThat(generated.contains("juno_land(")).isTrue();
        assertThat(generated.contains("juno_lor(")).isTrue();
        assertThat(generated.contains("juno_lxor(")).isTrue();
        assertThat(generated.contains("(juno_l > juno_r) - (juno_l < juno_r)")).as("lcmp lowers to a plain int result").isTrue();
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

        assertThat(generated.contains("static int64_t juno_demo_LongParam_identity_")).isTrue();
        assertThat(generated.contains("(int64_t arg0)")).isTrue();
        assertThat(generated.contains("locals[0].i32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<uint64_t>(arg0)))")).isTrue();
        assertThat(generated.contains("locals[1].i32 = static_cast<int32_t>(static_cast<uint32_t>(static_cast<uint64_t>(arg0) >> 32))")).isTrue();
        assertThat(generated.contains("static int64_t juno_field_demo_LongParam_saved_")).isTrue();
        assertThat(generated.contains("static_cast<float>(")).isTrue();
        assertThat(generated.contains("juno_f2l(")).isTrue();
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

        assertThat(generated.contains("union JunoSlot")).isTrue();
        assertThat(generated.contains("float v")).isTrue();
        assertThat(generated.contains("fmodf(")).isTrue();
        assertThat(generated.contains("juno_f2i(")).isTrue();
        assertThat(generated.contains("static_cast<float>(")).isTrue();
        assertThat(generated.contains("isnan(")).isTrue();
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

        assertThat(generated.contains("static float juno_demo_FloatMethods_mix_")).isTrue();
        assertThat(generated.contains("(float arg0, int32_t arg1, float arg2)")).isTrue();
        assertThat(generated.contains("locals[0].f32 = arg0;")).isTrue();
        assertThat(generated.contains("locals[1].i32 = arg1;")).isTrue();
        assertThat(generated.contains("locals[2].f32 = arg2;")).isTrue();
        assertThat(generated.contains("= juno_demo_FloatMethods_mix_")).isTrue();
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

        assertThat(generated.contains("static double juno_demo_DoubleMath_mix_")).isTrue();
        assertThat(generated.contains("(double arg0, int32_t arg1, double arg2)")).isTrue();
        assertThat(generated.contains("locals[0].f64 = arg0;")).isTrue();
        assertThat(generated.contains("locals[2].i32 = arg1;")).isTrue();
        assertThat(generated.contains("locals[3].f64 = arg2;")).isTrue();
        assertThat(generated.contains("sizeof(double) * (2)")).isTrue();
        assertThat(generated.contains("static double* juno_demo_DoubleMath_identity_")).isTrue();
        assertThat(generated.contains("static double juno_field_demo_DoubleMath_last_")).isTrue();
        assertThat(generated.contains("fmod(")).isTrue();
        assertThat(generated.contains("juno_d2i(")).isTrue();
        assertThat(generated.contains("juno_d2l(")).isTrue();
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

        assertThat(generated.contains("sizeof(int64_t) * (3)")).isTrue();
        assertThat(generated.contains("static int64_t* juno_demo_LongArray_identity_")).isTrue();
        assertThat(generated.contains("reinterpret_cast<int64_t*>(")).isTrue();
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

        assertThat(generated.contains("Closed-world entry point: demo.UsesEnum.main")).isTrue();
        assertThat(generated.contains("(int32_t arg0)")).as("an enum-typed parameter must be a plain int32_t, like every other Juno value").isTrue();
        // NORTH=0, SOUTH=1: Direction.SOUTH must resolve to the literal 1, Direction.NORTH to 0.
        assertThat(generated.contains(" = 1;")).as("Direction.SOUTH must fold to its ordinal, 1").isTrue();
        assertThat(generated.contains(" = 0;")).as("Direction.NORTH must fold to its ordinal, 0").isTrue();
        assertThat(generated.contains("getstatic")).as("getstatic must be resolved away, not passed through").isFalse();
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

        assertThat(generated.contains("Closed-world entry point: demo.UsesPoint.main")).isTrue();
        assertThat(generated.contains("struct JunoObject_demo_Point")).isTrue();
        assertThat(generated.contains("field_x_")).isTrue();
        assertThat(generated.contains("field_y_")).isTrue();
        assertThat(generated.contains("juno_alloc(sizeof(JunoObject_demo_Point)")).isTrue();
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

        assertThat(generated.contains("static int32_t juno_demo_TakesPoint_echo_")).isTrue();
        assertThat(generated.contains("static int32_t juno_demo_TakesPoint_sum_")).isTrue();
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

        assertThat(generated.contains("struct JunoObject_demo_NewsObject")).isTrue();
        assertThat(generated.contains("field_value_")).isTrue();
        assertThat(generated.contains("int32_t arg_receiver, int32_t arg0")).isTrue();
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

        assertThat(generated.contains("juno_ineg(")).isTrue();
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

        assertThat(generated.contains("juno_imul(")).isTrue();
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

        assertThat(generated.contains("struct JunoObject_demo_Labeled")).isTrue();
        assertThat(generated.contains("field_data_")).isTrue();
    }
}
