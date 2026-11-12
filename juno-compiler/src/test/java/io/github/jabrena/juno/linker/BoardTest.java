package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.backend.runtime.core.CoreRuntimes;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import io.github.jabrena.juno.board.Esp32Family;
import io.github.jabrena.juno.board.M5StackFamily;
import io.github.jabrena.juno.board.NanoFamily;
import io.github.jabrena.juno.board.PicoFamily;
import io.github.jabrena.juno.board.UnoGeneration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Board selection and capabilities")
class BoardTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Keeps UNO generations separate from other board families")
    void shouldClassifyBoardsByTheirOwnFamily() {
        assertThat(UnoGeneration.values()).containsExactly(UnoGeneration.R3, UnoGeneration.R4, UnoGeneration.Q);
        assertThat(Board.UNO_R3.family()).isEqualTo(UnoGeneration.R3);
        assertThat(Board.UNO_R4_WIFI.family()).isEqualTo(UnoGeneration.R4);
        assertThat(Board.UNO_Q.family()).isEqualTo(UnoGeneration.Q);
        assertThat(Board.UNO_R3_WIFI_REV2.family()).isEqualTo(UnoGeneration.R3);
        assertThat(Board.NANO_ESP32.family()).isEqualTo(NanoFamily.NANO_ESP32);
        assertThat(Board.AI_THINKER_ESP32_CAM.family()).isEqualTo(Esp32Family.AI_THINKER_ESP32_CAM);
        assertThat(Board.M5_CARDPUTER.family()).isEqualTo(M5StackFamily.CARDPUTER_ADV);
        assertThat(Board.PICO_2_W.family()).isEqualTo(PicoFamily.PICO_2);
        assertThat(Board.PICO_2_W.unoGeneration()).isEmpty();
    }

    static Stream<Arguments> capabilities() {
        return Stream.of(
                arguments(Board.UNO_R3, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_R3, Capability.WATCHDOG, true),
                arguments(Board.UNO_R3, Capability.WIFI, true),
                arguments(Board.UNO_R3, Capability.UUID_RANDOM, true),
                arguments(Board.UNO_R3, Capability.BIG_NUMBERS, true),
                arguments(Board.UNO_R3, Capability.WIFI_ACCESS_POINT, false),
                arguments(Board.UNO_R3, Capability.HTTP_CLIENT, true),
                arguments(Board.UNO_R3, Capability.HTTPS_CLIENT, false),
                arguments(Board.UNO_R4_WIFI, Capability.WIFI_ACCESS_POINT, true),
                arguments(Board.UNO_R4_WIFI, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_R4_WIFI, Capability.WATCHDOG, true),
                arguments(Board.UNO_R4_WIFI, Capability.WIFI, true),
                arguments(Board.UNO_R4_WIFI, Capability.HTTPS_CLIENT, true),
                arguments(Board.UNO_R4_WIFI, Capability.LED_MATRIX, true),
                arguments(Board.UNO_R4_WIFI, Capability.USB_HID, true),
                arguments(Board.UNO_R4_WIFI, Capability.HTTPS_SERVER, false),
                arguments(Board.UNO_Q, Capability.WIFI, true),
                arguments(Board.UNO_Q, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_Q, Capability.HTTPS_CLIENT, true),
                arguments(Board.UNO_Q, Capability.LED_MATRIX, true),
                arguments(Board.UNO_Q, Capability.HTTP_SERVER, true),
                arguments(Board.UNO_Q, Capability.HTTPS_SERVER, true),
                arguments(Board.UNO_Q, Capability.SMTP_STARTTLS, false),
                arguments(Board.UNO_Q, Capability.WATCHDOG, false),
                arguments(Board.UNO_Q, Capability.USB_HID, false),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.WATCHDOG, true),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.HTTP_SERVER, true),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.HTTPS_SERVER, false),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.ONBOARD_IMU, true),
                arguments(Board.UNO_R3, Capability.BLUETOOTH_LE, false),
                arguments(Board.UNO_R3_WIFI_REV2, Capability.BLUETOOTH_LE, false),
                arguments(Board.UNO_R4_WIFI, Capability.ONBOARD_IMU, false),
                arguments(Board.UNO_Q, Capability.ONBOARD_IMU, false),
                arguments(Board.NANO_ESP32, Capability.WIFI, true),
                arguments(Board.NANO_ESP32, Capability.CLASS_INHERITANCE, true),
                arguments(Board.NANO_ESP32, Capability.ONBOARD_WIFI, true),
                arguments(Board.NANO_ESP32, Capability.WIFI_ACCESS_POINT, true),
                arguments(Board.NANO_ESP32, Capability.HTTP_CLIENT, true),
                arguments(Board.NANO_ESP32, Capability.HTTPS_CLIENT, true),
                arguments(Board.NANO_ESP32, Capability.HTTP_SERVER, true),
                arguments(Board.NANO_ESP32, Capability.HTTPS_SERVER, true),
                arguments(Board.NANO_ESP32, Capability.TLS_EMAIL, true),
                arguments(Board.NANO_ESP32, Capability.SMTP_STARTTLS, true),
                arguments(Board.NANO_ESP32, Capability.SECURE_ENTROPY, true),
                arguments(Board.NANO_ESP32, Capability.BIG_NUMBERS, true),
                arguments(Board.NANO_ESP32, Capability.BLUETOOTH_LE, true),
                arguments(Board.NANO_ESP32, Capability.WATCHDOG, true),
                arguments(Board.NANO_ESP32, Capability.USB_HID, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.BIG_NUMBERS, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.BLUETOOTH_LE, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.CAMERA, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.HTTP_STREAM, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.HTTP_SERVER, true),
                arguments(Board.NANO_ESP32, Capability.HTTP_STREAM, false),
                arguments(Board.NANO_ESP32, Capability.CAMERA, false),
                arguments(Board.M5_CARDPUTER, Capability.BIG_NUMBERS, true),
                arguments(Board.M5_CARDPUTER, Capability.CLASS_INHERITANCE, true),
                arguments(Board.M5_CARDPUTER, Capability.USB_HID, true),
                arguments(Board.M5_CARDPUTER, Capability.WIFI, true),
                arguments(Board.M5_CARDPUTER, Capability.ONBOARD_WIFI, true),
                arguments(Board.M5_CARDPUTER, Capability.WIFI_ACCESS_POINT, true),
                arguments(Board.M5_CARDPUTER, Capability.HTTP_CLIENT, true),
                arguments(Board.M5_CARDPUTER, Capability.HTTPS_CLIENT, true),
                arguments(Board.M5_CARDPUTER, Capability.HTTP_SERVER, true),
                arguments(Board.M5_CARDPUTER, Capability.HTTPS_SERVER, true),
                arguments(Board.M5_CARDPUTER, Capability.M5_DISPLAY, true),
                arguments(Board.NANO_ESP32, Capability.M5_DISPLAY, false),
                arguments(Board.M5_CARDPUTER, Capability.M5_KEYBOARD, true),
                arguments(Board.NANO_ESP32, Capability.M5_KEYBOARD, false),
                arguments(Board.M5_CARDPUTER, Capability.M5_AUDIO, true),
                arguments(Board.M5_CARDPUTER, Capability.M5_BATTERY, true),
                arguments(Board.M5_CARDPUTER, Capability.WATCHDOG, true),
                arguments(Board.M5_CARDPUTER, Capability.SECURE_ENTROPY, true),
                arguments(Board.M5_CARDPUTER, Capability.STRUCTURED_CONCURRENCY, true),
                arguments(Board.M5_CARDPUTER, Capability.DOUBLE_MATH, true),
                arguments(Board.M5_CARDPUTER, Capability.BLUETOOTH_LE, true),
                arguments(Board.M5_CARDPUTER, Capability.TLS_EMAIL, true),
                arguments(Board.PICO_2_W, Capability.HTTPS_CLIENT, true),
                arguments(Board.PICO_2_W, Capability.HTTPS_SERVER, true),
                arguments(Board.PICO_2_W, Capability.TLS_EMAIL, true),
                arguments(Board.PICO_2_W, Capability.BLUETOOTH_LE, true),
                arguments(Board.PICO_2_W, Capability.WATCHDOG, true),
                arguments(Board.PICO_2_W, Capability.USB_HID, true),
                arguments(Board.PICO_2_W, Capability.STRUCTURED_CONCURRENCY, true),
                arguments(Board.PICO_2_W, Capability.DOUBLE_MATH, true),
                arguments(Board.NANO_ESP32, Capability.M5_AUDIO, false),
                arguments(Board.NANO_ESP32, Capability.M5_BATTERY, false));
    }

    @Test
    @DisplayName("The M5Stack Cardputer resolves to its ESP32-S3 target")
    void shouldResolveM5StackCardputerTarget() {
        // Given
        Board board = Board.fromId("M5STACK-CARDPUTER");

        // When & Then
        assertThat(board).isEqualTo(Board.M5_CARDPUTER);
        assertThat(board.annotationArgument()).isEqualTo("M5StackCardputerBoard.class");
        assertThat(board.fqbn())
                .isEqualTo("m5stack:esp32:m5stack_cardputer:USBMode=default,CDCOnBoot=cdc,UploadMode=cdc");
        assertThat(board.core()).isEqualTo(ArduinoCore.M5STACK_ESP32);
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/M5StackCardputerBoard")).isEqualTo(board);
    }

    @Test
    @DisplayName("The Uno boards resolve from their board IDs")
    void shouldResolveTheUnoBoardsFromTheirIds() {
        assertThat(Board.fromId("arduino-uno-q")).isEqualTo(Board.UNO_Q);
        assertThat(Board.fromId("arduino-uno-r4-wifi")).isEqualTo(Board.UNO_R4_WIFI);
        assertThat(Board.fromId("arduino-uno-r3-wifi-rev2")).isEqualTo(Board.UNO_R3_WIFI_REV2);
        assertThat(Board.fromId("arduino-uno-r3")).isEqualTo(Board.UNO_R3);
        assertThatThrownBy(() -> Board.fromId("arduino-one-q"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("arduino-one-q");
    }

    @Test
    @DisplayName("The AI Thinker ESP32-CAM resolves to its Espressif target")
    void shouldResolveAiThinkerEsp32CamTarget() {
        // Given
        Board board = Board.fromId("AI-THINKER-ESP32-CAM");

        // When & Then
        assertThat(board).isEqualTo(Board.AI_THINKER_ESP32_CAM);
        assertThat(board.annotationArgument()).isEqualTo("AiThinkerEsp32CamBoard.class");
        assertThat(board.fqbn()).isEqualTo("esp32:esp32:esp32cam");
        assertThat(board.core()).isEqualTo(ArduinoCore.ESP32_CAM);
        assertThat(board.core().architecture()).isEqualTo(Architecture.XTENSA);
        assertThat(new CoreRuntimes().of(board.core()).builtinLedPin()).isEqualTo(33);
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/AiThinkerEsp32CamBoard"))
                .isEqualTo(board);
    }

    @Test
    @DisplayName("The AI Thinker ESP32-CAM runs threads on both cores and has HTTP and HTTPS clients")
    void shouldGiveTheEsp32CamThreadsAndHttpClients() {
        // Given
        Board board = Board.AI_THINKER_ESP32_CAM;

        // When & Then
        assertThat(new boolean[] {board.supports(Capability.STRUCTURED_CONCURRENCY),
                board.supports(Capability.HTTP_CLIENT), board.supports(Capability.HTTPS_CLIENT)})
                .containsOnly(true);
        assertThat(new CoreRuntimes().of(board.core()).carrierCount()).isEqualTo(2);
        assertThat(new CoreRuntimes().of(board.core()).httpsHelpers(false))
                .contains("setCACertBundle(juno_ca_bundle, sizeof(juno_ca_bundle));");
        assertThat(new CoreRuntimes().of(ArduinoCore.ESP32).httpsHelpers(false))
                .contains("setCACertBundle(juno_ca_bundle);");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W resolves to its arduino-pico Thumb-2 target")
    void shouldResolveRaspberryPico2WTarget() {
        // Given
        Board board = Board.fromId("Raspberry-Pi-Pico-2-W");

        // When & Then
        assertThat(board).isEqualTo(Board.PICO_2_W);
        assertThat(board.annotationArgument()).isEqualTo("RaspberryPico2WifiBoard.class");
        assertThat(board.fqbn()).isEqualTo("rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble");
        assertThat(board.core()).isEqualTo(ArduinoCore.RP2350);
        assertThat(board.core().architecture()).isEqualTo(Architecture.THUMB2);
        assertThat(new CoreRuntimes().of(board.core()).builtinLedPin()).isEqualTo(64);
        assertThat(board.supports(Capability.WIFI)).isTrue();
        assertThat(board.supports(Capability.HTTP_CLIENT)).isTrue();
        assertThat(board.supports(Capability.HTTP_SERVER)).isTrue();
        assertThat(board.supports(Capability.HTTPS_CLIENT)).isTrue();
        assertThat(board.supports(Capability.HTTPS_SERVER)).isTrue();
        assertThat(board.supports(Capability.TLS_EMAIL)).isTrue();
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/RaspberryPico2WifiBoard"))
                .isEqualTo(board);
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W blink drives the CYW43 LED pin with the core's own delay")
    void shouldLowerRaspberryPico2BlinkToTheCyw43Led() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
                import io.github.jabrena.juno.util.Delay;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoBlink {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(RaspberryPico2.getGpio().getBuiltinLed());
                        while (true) {
                            led.high();
                            Delay.millis(500);
                            led.low();
                            Delay.millis(500);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoBlink", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoBlink", false, Optional.empty());

        // Then
        assertThat(result.report().board()).isEqualTo(Board.PICO_2_W);
        assertThat(result.assembly()).contains("#64", "bl delay\n");
        assertThat(result.runtimeShim()).doesNotContain("Arduino_LED_Matrix.h", "WiFi.h");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W resolves A0..A2 to GP26..GP28 and accepts GP names")
    void shouldResolveRaspberryPico2PinNames() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoPins {
                    public static void main(String[] args) {
                        Gpio gpio = RaspberryPico2.getGpio();
                        DigitalOutput.of(gpio.digitalPin("GP16")).high();
                        gpio.analogRead(gpio.analogPin("A0"));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoPins", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoPins", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim())
                .contains("juno_gpio_analog_pins[] = {\n26, 27, 28, -1, -1, -1, -1};")
                .contains("16, 17, 18, 19, 20, 21, 22, 26, 27, 28, -1};")
                .contains("} else if (name[1] == 'P') {\n      // The Raspberry Pi Pico's own GPn pin names.\n      offset = 2;");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W joins Wi-Fi through the core's WiFi library")
    void shouldLowerRaspberryPico2WifiToTheCoreWifiLibrary() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.net.Wifi;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoWifi {
                    public static void main(String[] args) {
                        Wifi wifi = RaspberryPico2.getWifi();
                        wifi.begin("ssid", "password");
                        wifi.beginAP("juno", "password");
                        int[] address = new int[4];
                        wifi.localIP(address);
                        wifi.status();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoWifi", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoWifi", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim())
                .contains("#include <WiFi.h>\n", "WiFi.begin(ssid, password);", "WiFi.beginAP(ssid, password);")
                .doesNotContain("WiFiS3.h");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W emits the core Wi-Fi HTTP server transport")
    void shouldLowerRaspberryPico2HttpServerToTheCoreWifiServer() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.net.http.HttpServer;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoHttpServer {
                    public static void main(String[] args) {
                        RaspberryPico2.getWifi();
                        HttpServer.begin(80);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoHttpServer", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoHttpServer", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim())
                .contains("#include <WiFi.h>\n", "WiFiServer", "juno_http_server_begin")
                .doesNotContain("WiFiS3.h");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W adapts its BTstack BLE library to the Powered Up central")
    void shouldLowerRaspberryPico2BluetoothLeToTheCoreBleLibrary() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.net.BluetoothLe;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoBluetooth {
                    public static void main(String[] args) {
                        BluetoothLe bluetooth = RaspberryPico2.getBluetoothLe();
                        byte[] advertisement = new byte[8];
                        bluetooth.connect("00001623-1212-efde-1623-785feabcd123", "00001624-1212-efde-1623-785feabcd123",
                                1000, advertisement, advertisement.length);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoBluetooth", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoBluetooth", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim()).contains("#include <BLE.h>", "BLE.scan", "JunoPicoBleDevice")
                .doesNotContain("ArduinoBLE.h");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W lowers USB HID mouse calls to Mouse.h")
    void shouldLowerRaspberryPico2MouseToTheCoreUsbHid() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                import io.github.jabrena.juno.hardware.hid.Mouse;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoMouse {
                    public static void main(String[] args) {
                        Mouse mouse = RaspberryPico2.getMouse();
                        mouse.begin();
                        mouse.move(1, -1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoMouse", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoMouse", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim()).contains("#include <Mouse.h>", "juno_mouse_begin", "juno_mouse_move");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W starts its I2C bus on the Grove shield's I2C0 pins")
    void shouldStartRaspberryPico2I2cOnGp8AndGp9() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.hardware.RaspberryPico2;
                @Board({RaspberryPico2WifiBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class PicoI2c {
                    public static void main(String[] args) {
                        RaspberryPico2.getI2c().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoI2c", source);
        JunoCompiler juno = new JunoCompiler();

        // When
        CompilationResult pico = juno.compile(CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoI2c", false,
                Optional.of(Board.PICO_2_W.id()));
        CompilationResult r4 = juno.compile(CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoI2c", false,
                Optional.of(Board.UNO_R4_WIFI.id()));

        // Then
        assertThat(pico.runtimeShim()).contains("  Wire.setSDA(8);\n  Wire.setSCL(9);\n  Wire.begin();");
        assertThat(r4.runtimeShim()).contains("juno_i2c_begin() {\n  Wire.begin();").doesNotContain("Wire.setSDA");
    }

    @Test
    @DisplayName("The Cardputer and ESP32-CAM start their I2C buses on their Grove and SCCB pins")
    void shouldStartCardputerAndEsp32CamI2cOnTheirBoardPins() throws Exception {
        // Given
        String cardputerSource = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                import io.github.jabrena.juno.hardware.M5StackCardputer;
                @Board(M5StackCardputerBoard.class)
                public final class CardputerI2c {
                    public static void main(String[] args) {
                        M5StackCardputer.getI2c().begin();
                    }
                }
                """;
        String camSource = """
                package demo;
                import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.Esp32Cam;
                @Board(AiThinkerEsp32CamBoard.class)
                public final class CamI2c {
                    public static void main(String[] args) {
                        Esp32Cam.getI2c().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CardputerI2c", cardputerSource);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CamI2c", camSource);
        JunoCompiler juno = new JunoCompiler();

        // When
        CompilationResult cardputer = juno.compile(CompilerTestSupport.classpath(temporaryDirectory),
                "demo.CardputerI2c", false, Optional.of(Board.M5_CARDPUTER.id()));
        CompilationResult cam = juno.compile(CompilerTestSupport.classpath(temporaryDirectory), "demo.CamI2c", false,
                Optional.of(Board.AI_THINKER_ESP32_CAM.id()));

        // Then
        assertThat(cardputer.runtimeShim()).contains("Wire.setPins(2, 1);\n  Wire.begin();");
        assertThat(cam.runtimeShim()).contains("Wire.setPins(26, 27);\n  Wire.begin();");
    }

    @Test
    @DisplayName("The ESP32-CAM rejects one program that uses both its camera and its I2C bus")
    void shouldRejectEsp32CamCameraAndI2cTogether() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.Esp32Cam;
                @Board(AiThinkerEsp32CamBoard.class)
                public final class CamAndI2c {
                    public static void main(String[] args) {
                        Esp32Cam.getCamera();
                        Esp32Cam.getI2c().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CamAndI2c", source);
        JunoCompiler juno = new JunoCompiler();

        // When & Then
        assertThatThrownBy(() -> juno.compile(CompilerTestSupport.classpath(temporaryDirectory), "demo.CamAndI2c",
                false, Optional.of(Board.AI_THINKER_ESP32_CAM.id())))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Esp32Cam.getCamera() and Esp32Cam.getI2c()")
                .hasMessageContaining("GPIO26 and GPIO27");
    }

    @Test
    @DisplayName("The Raspberry Pi Pico 2 W lowers @Watchdog to the RP2350 hardware watchdog")
    void shouldLowerWatchdogOnRaspberryPico2W() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.util.Delay;
                @Board(RaspberryPico2WifiBoard.class)
                @Watchdog
                public final class PicoWatchdog {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PicoWatchdog", source);

        // When
        CompilationResult result = new JunoCompiler().compile(
                CompilerTestSupport.classpath(temporaryDirectory), "demo.PicoWatchdog", false, Optional.empty());

        // Then
        assertThat(result.runtimeShim()).contains("#include <hardware/watchdog.h>")
                .contains("watchdog_enable(5000u, true);")
                .contains("watchdog_update();");
    }

    @Test
    @DisplayName("A requested board is required when the class has no annotation")
    void shouldRequireARequestedBoardWithoutAnAnnotation() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.util.Delay;
                public final class Unannotated {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unannotated", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Unannotated", Optional.empty()))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Target board is required")
                .hasMessageContaining("-Djuno.board=<id>")
                .hasMessageContaining("--board=<id>");
    }

    @Test
    @DisplayName("The UNO Q runs under the Zephyr core")
    void shouldRunTheUnoQUnderTheZephyrCore() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", unoQDelaySource());

        // When
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.OnUnoQ", Optional.empty());

        // Then
        assertThat(program.board()).isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("The UNO Q board carries its FQBN")
    void shouldGiveTheUnoQItsFqbn() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", unoQDelaySource());
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.OnUnoQ", Optional.empty());

        // When & Then
        assertThat(program.board().fqbn()).isEqualTo(Board.UNO_Q.fqbn());
    }

    @Test
    @DisplayName("The UNO Q calls the Zephyr delay wrappers and not the Arduino core functions")
    void shouldCallTheZephyrDelayWrappersOnTheUnoQ() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", unoQDelaySource());
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoQ");

        // When & Then
        // Zephyr inlines delay()/delayMicroseconds() and provides yield() itself.
        assertThat(result.assembly()).contains("bl juno_delay\n", "bl juno_delay_microseconds\n")
                .doesNotContain("bl delay\n", "bl delayMicroseconds\n");
    }

    @Test
    @DisplayName("The UNO Q shim defines the Zephyr delay and omits the LED matrix library")
    void shouldDefineTheZephyrDelayAndOmitTheLedMatrixLibraryOnTheUnoQ() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", unoQDelaySource());
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoQ");

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_delay(uint32_t ms)")
                .doesNotContain("extern \"C\" void yield()", "Arduino_LED_Matrix", "ArduinoLEDMatrix");
    }

    @Test
    @DisplayName("The UNO Q drives its thirteen-column LED matrix through the Zephyr library")
    void shouldDriveTheThirteenColumnLedMatrixThroughTheZephyrLibrary() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                @Board(ArduinoUnoQBoard.class)
                public final class MatrixOnUnoQ {
                    public static void main(String[] args) {
                        ArduinoUno.getLedMatrix().begin();
                        ArduinoUno.getLedMatrix().loadFrame(1, 2, 3, 4);
                        int columns = ArduinoUno.getLedMatrix().columns();
                        ArduinoUno.getLedMatrix().clear();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MatrixOnUnoQ", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MatrixOnUnoQ");

        // When & Then
        // Zephyr's library is Arduino_LED_Matrix and a 13x8 frame (104 pixels) needs four words.
        assertThat(result.runtimeShim()).contains("#include \"Arduino_LED_Matrix.h\"",
                "static Arduino_LED_Matrix juno_led_matrix;", "const uint32_t frame[4] = {")
                .doesNotContain("ArduinoLEDMatrix", "static_cast<void>(word3)");
    }

    @Test
    @DisplayName("The UNO Q LED matrix has thirteen columns in the assembly")
    void shouldUseThirteenColumnsForTheUnoQLedMatrix() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                @Board(ArduinoUnoQBoard.class)
                public final class MatrixOnUnoQ {
                    public static void main(String[] args) {
                        ArduinoUno.getLedMatrix().begin();
                        ArduinoUno.getLedMatrix().loadFrame(1, 2, 3, 4);
                        int columns = ArduinoUno.getLedMatrix().columns();
                        ArduinoUno.getLedMatrix().clear();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.MatrixOnUnoQ", source);

        // When & Then
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.MatrixOnUnoQ").assembly())
                .contains("#13");
    }

    @Test
    @DisplayName("The UNO R4 keeps its own yield and LED matrix")
    void shouldKeepItsOwnYieldAndLedMatrixOnTheUnoR4() throws Exception {
        // Given
        CompilationResult result = compileUnoR4();

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" void yield()", "#include \"Arduino_LED_Matrix.h\"",
                "static ArduinoLEDMatrix juno_led_matrix;", "const uint32_t frame[3] = {",
                "static_cast<void>(word3)");
    }

    @Test
    @DisplayName("The UNO R4 calls the Arduino delay function and not the Juno wrapper")
    void shouldCallTheArduinoDelayOnTheUnoR4() throws Exception {
        // Given
        CompilationResult result = compileUnoR4();

        // When & Then
        assertThat(result.assembly()).contains("bl delay\n").doesNotContain("juno_delay");
    }

    @Test
    @DisplayName("The UNO R4 LED matrix has twelve columns in the assembly")
    void shouldUseTwelveColumnsForTheUnoR4LedMatrix() throws Exception {
        // Given
        CompilationResult result = compileUnoR4();

        // When & Then
        assertThat(result.assembly()).contains("#12");
    }

    @Test
    @DisplayName("The UNO Q has no watchdog")
    void shouldRejectAWatchdogOnTheUnoQ() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.util.Delay;
                @Board(ArduinoUnoQBoard.class)
                @Watchdog(timeoutMillis = 3000)
                public final class WatchdogOnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogOnUnoQ", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.WatchdogOnUnoQ", Optional.empty()))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO Q's Zephyr firmware does not enable its watchdog subsystem");
    }

    @Test
    @DisplayName("Declaring multiple boards without a requested board fails")
    void shouldFailWhenMultipleBoardsAreDeclaredWithoutARequestedBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Portable", Optional.empty()))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("@Board declares multiple boards")
                .hasMessageContaining("UNO Q")
                .hasMessageContaining("UNO R4 WiFi");
    }

    @Test
    @DisplayName("A requested board picks one of the declared boards")
    void shouldPickARequestedBoardFromTheDeclaredBoards() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.Portable",
                Optional.of(Board.UNO_Q.id())).board()).isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("A requested board picks the R4 when it is one of the declared boards")
    void shouldPickTheR4WhenItIsARequestedDeclaredBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.Portable",
                Optional.of(Board.UNO_R4_WIFI.id())).board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    @DisplayName("A requested board that is not declared by @Board fails")
    void shouldFailForARequestedBoardNotDeclaredByBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board(ArduinoUnoQBoard.class)
                public final class OnlyUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnlyUnoQ", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.OnlyUnoQ",
                Optional.of(Board.UNO_R4_WIFI.id())))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("is not one of @Board's declared boards");
    }

    @Test
    @DisplayName("A class without a board annotation takes the requested board")
    void shouldLetAClassWithoutBoardTakeTheRequestedBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.util.Delay;
                public final class Unrestricted {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unrestricted", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.Unrestricted",
                Optional.of(Board.UNO_Q.id())).board()).isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("An unknown requested board id fails")
    void shouldFailForAnUnknownRequestedBoardId() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.util.Delay;
                public final class Unannotated2 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unannotated2", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Unannotated2",
                Optional.of("arduino-mega")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Unknown board 'arduino-mega'");
    }

    @Test
    @DisplayName("A portable program using the LED matrix links for each declared board")
    void shouldLinkAPortableLedMatrixProgramForEachDeclaredBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                @Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class PortableWithLedMatrix {
                    public static void main(String[] args) {
                        ArduinoUno.getLedMatrix().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableWithLedMatrix", source);

        // When & Then
        // Both boards have an LED matrix (12x8 and 13x8), so each declared board builds the same source.
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.PortableWithLedMatrix",
                Optional.of(Board.UNO_R4_WIFI.id())).board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    @DisplayName("The portable LED matrix program also links for the UNO Q")
    void shouldLinkThePortableLedMatrixProgramForTheUnoQ() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                @Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class PortableWithLedMatrix {
                    public static void main(String[] args) {
                        ArduinoUno.getLedMatrix().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableWithLedMatrix", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.PortableWithLedMatrix",
                Optional.of(Board.UNO_Q.id())).board()).isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("A Powered Up hub built on the Bluetooth LE radio is rejected on the UNO R3 at compile time")
    void shouldRejectTheBluetoothLeRadioOnTheUnoR3() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                @Board(ArduinoUnoR3Board.class)
                public final class NoRadio {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
                        hub.connect(1000);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoRadio", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.NoRadio", Optional.empty()))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("UNO R3 has no Bluetooth LE adapter reachable through ArduinoBLE");
    }

    @Test
    @DisplayName("A Powered Up hub program is portable across both boards")
    void shouldLinkAPoweredUpHubProgramForBothBoards() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
                        if (hub.connect(1000)) {
                            hub.getMotor(HubPort.A).drive(50);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableTrain", source);

        // When & Then
        // Both boards provide Bluetooth LE: the R4 WiFi through its ESP32-S3, the UNO Q through
        // its Linux side's adapter, tunnelled over Arduino_RouterBridge.
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.PortableTrain",
                Optional.of(Board.UNO_Q.id())).board()).isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("The Powered Up hub program also links for the R4 WiFi")
    void shouldLinkThePoweredUpHubProgramForTheR4WiFi() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
                        if (hub.connect(1000)) {
                            hub.getMotor(HubPort.A).drive(50);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableTrain", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.PortableTrain",
                Optional.of(Board.UNO_R4_WIFI.id())).board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    @DisplayName("The UNO Q supports portable Wi-Fi, UDP, HTTPS and secure email")
    void shouldSupportPortableWifiUdpHttpsAndSecureEmailOnTheUnoQ() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                import io.github.jabrena.juno.hardware.net.Wifi;
                import java.io.IOException;
                import java.net.DatagramSocket;
                import io.github.jabrena.juno.hardware.net.http.HttpClient;
                import io.github.jabrena.juno.hardware.net.email.Pop3Client;
                import io.github.jabrena.juno.hardware.net.email.Smtp;
                @Board(ArduinoUnoQBoard.class)
                public final class WifiOnUnoQ {
                    public static void main(String[] args) {
                        ArduinoUno.getWifi().status();
                        try {
                            new DatagramSocket(5000).close();
                        } catch (IOException e) {
                            return;
                        }
                        byte[] body = new byte[8];
                        byte[] headers = new byte[8];
                        int[] result = new int[2];
                        HttpClient.get("https://example.com/", body, 8, headers, 8, result);
                        Smtp.sendTls("mail.example.com", 465, "me@example.com", "secret",
                                "me@example.com", "me@example.com", "Hello", "Hello");
                        Pop3Client.messageCount("mail.example.com", 995, "me@example.com", "secret");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiOnUnoQ", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.WifiOnUnoQ", Optional.empty()).board())
                .isEqualTo(Board.UNO_Q);
    }

    @Test
    @DisplayName("Each board declares its Arduino core")
    void shouldDeclareEachBoardsArduinoCore() {
        // When & Then
        assertThat(new ArduinoCore[] {Board.UNO_R3.core(), Board.UNO_R4_WIFI.core(), Board.UNO_Q.core()})
                .containsExactly(ArduinoCore.CLASSIC_AVR, ArduinoCore.RENESAS_UNO, ArduinoCore.ZEPHYR);
    }

    @Test
    @DisplayName("The UNO R4 WiFi supports every capability except Cardputer hardware, its IMU and the TLS server")
    void shouldSupportEveryGeneralCapabilityOnTheUnoR4WiFi() {
        // When & Then
        assertThat(Capability.values()).filteredOn(capability -> capability != Capability.M5_DISPLAY
                        && capability != Capability.M5_KEYBOARD && capability != Capability.M5_AUDIO
                        && capability != Capability.M5_BATTERY && capability != Capability.HTTPS_SERVER
                        && capability != Capability.ONBOARD_IMU && capability != Capability.CAMERA
                        && capability != Capability.HTTP_STREAM)
                .allMatch(Board.UNO_R4_WIFI::supports);
    }

    @ParameterizedTest(name = "{0} supports {1}: {2}")
    @MethodSource("capabilities")
    @DisplayName("Each board declares its capabilities")
    void shouldDeclareEachBoardsCapabilities(Board board, Capability capability, boolean expected) {
        // When & Then
        assertThat(board.supports(capability)).isEqualTo(expected);
    }

    private static String unoQDelaySource() {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board(ArduinoUnoQBoard.class)
                public final class OnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                        Delay.micros(1);
                    }
                }
                """;
    }

    private CompilationResult compileUnoR4() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.ArduinoUno;
                @Board(ArduinoUnoR4WiFiBoard.class)
                public final class OnUnoR4 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                        ArduinoUno.getLedMatrix().begin();
                        int columns = ArduinoUno.getLedMatrix().columns();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoR4", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoR4");
    }
}
