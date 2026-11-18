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
import io.github.jabrena.juno.board.MbedFamily;
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
        assertThat(UnoGeneration.values()).containsExactly(UnoGeneration.R3, UnoGeneration.R4);
        assertThat(Board.UNO_R3.family()).isEqualTo(UnoGeneration.R3);
        assertThat(Board.UNO_R4_WIFI.family()).isEqualTo(UnoGeneration.R4);
        assertThat(Board.NANO_ESP32.family()).isEqualTo(NanoFamily.NANO_ESP32);
        assertThat(Board.AI_THINKER_ESP32_CAM.family()).isEqualTo(Esp32Family.AI_THINKER_ESP32_CAM);
        assertThat(Board.M5_CARDPUTER.family()).isEqualTo(M5StackFamily.CARDPUTER_ADV);
        assertThat(Board.PICO_2_W.family()).isEqualTo(PicoFamily.PICO_2);
        assertThat(Board.PORTENTA_H7.family()).isEqualTo(MbedFamily.PORTENTA_H7);
        assertThat(Board.PICO_2_W.unoGeneration()).isEmpty();
        assertThat(Board.PORTENTA_H7.unoGeneration()).isEmpty();
    }

    static Stream<Arguments> capabilities() {
        return Stream.of(
                arguments(Board.M5_CARDPUTER, Capability.ONBOARD_SD_CARD, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.ONBOARD_SD_CARD, true),
                arguments(Board.UNO_R4_WIFI, Capability.ONBOARD_SD_CARD, false),
                arguments(Board.NANO_ESP32, Capability.ONBOARD_SD_CARD, false),
                arguments(Board.UNO_R3, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_R3, Capability.WATCHDOG, true),
                arguments(Board.UNO_R3, Capability.WIFI, false),
                arguments(Board.PORTENTA_H7, Capability.BIG_NUMBERS, true),
                arguments(Board.PORTENTA_H7, Capability.WIFI, true),
                arguments(Board.PORTENTA_H7, Capability.ONBOARD_WIFI, true),
                arguments(Board.PORTENTA_H7, Capability.WIFI_ACCESS_POINT, true),
                arguments(Board.PORTENTA_H7, Capability.HTTP_CLIENT, true),
                arguments(Board.PORTENTA_H7, Capability.HTTPS_CLIENT, true),
                arguments(Board.PORTENTA_H7, Capability.HTTP_SERVER, true),
                arguments(Board.PORTENTA_H7, Capability.HTTPS_SERVER, true),
                arguments(Board.PORTENTA_H7, Capability.BLUETOOTH_LE, true),
                arguments(Board.PORTENTA_H7, Capability.USB_HID, true),
                arguments(Board.PORTENTA_H7, Capability.WATCHDOG, true),
                arguments(Board.PORTENTA_H7, Capability.UUID_RANDOM, true),
                arguments(Board.PORTENTA_H7, Capability.DOUBLE_MATH, true),
                arguments(Board.PORTENTA_H7, Capability.STRUCTURED_CONCURRENCY, true),
                arguments(Board.UNO_R3, Capability.UUID_RANDOM, true),
                arguments(Board.UNO_R3, Capability.BIG_NUMBERS, true),
                arguments(Board.UNO_R3, Capability.WIFI_ACCESS_POINT, false),
                arguments(Board.UNO_R3, Capability.HTTP_CLIENT, false),
                arguments(Board.UNO_R3, Capability.HTTPS_CLIENT, false),
                arguments(Board.UNO_R4_WIFI, Capability.WIFI_ACCESS_POINT, true),
                arguments(Board.UNO_R4_WIFI, Capability.CLASS_INHERITANCE, true),
                arguments(Board.UNO_R4_WIFI, Capability.WATCHDOG, true),
                arguments(Board.UNO_R4_WIFI, Capability.WIFI, true),
                arguments(Board.UNO_R4_WIFI, Capability.HTTPS_CLIENT, true),
                arguments(Board.UNO_R4_WIFI, Capability.LED_MATRIX, true),
                arguments(Board.UNO_R4_WIFI, Capability.USB_HID, true),
                arguments(Board.UNO_R4_WIFI, Capability.HTTPS_SERVER, false),
                arguments(Board.UNO_R4_WIFI, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.NANO_ESP32, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.M5_CARDPUTER, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.PICO_2_W, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.PORTENTA_H7, Capability.JDK_HTTP_CLIENT, true),
                arguments(Board.UNO_R3, Capability.JDK_HTTP_CLIENT, false),
                arguments(Board.UNO_R4_WIFI, Capability.JSON, true),
                arguments(Board.NANO_ESP32, Capability.JSON, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.JSON, true),
                arguments(Board.M5_CARDPUTER, Capability.JSON, true),
                arguments(Board.PICO_2_W, Capability.JSON, true),
                arguments(Board.PORTENTA_H7, Capability.JSON, true),
                arguments(Board.UNO_R3, Capability.JSON, false),
                arguments(Board.UNO_R3, Capability.BLUETOOTH_LE, false),
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
                arguments(Board.NANO_ESP32, Capability.PSRAM, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.BIG_NUMBERS, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.BLUETOOTH_LE, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.CAMERA, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.PSRAM, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.HTTP_STREAM, true),
                arguments(Board.AI_THINKER_ESP32_CAM, Capability.HTTP_SERVER, true),
                arguments(Board.NANO_ESP32, Capability.HTTP_STREAM, false),
                arguments(Board.NANO_ESP32, Capability.CAMERA, false),
                arguments(Board.M5_CARDPUTER, Capability.BIG_NUMBERS, true),
                arguments(Board.M5_CARDPUTER, Capability.PSRAM, false),
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
                arguments(Board.M5_CARDPUTER, Capability.DISPLAY, true),
                arguments(Board.UNO_R4_WIFI, Capability.DISPLAY, true),
                arguments(Board.NANO_ESP32, Capability.DISPLAY, false),
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
        assertThat(Board.fromId("arduino-uno-r4-wifi")).isEqualTo(Board.UNO_R4_WIFI);
        assertThat(Board.fromId("arduino-uno-r3")).isEqualTo(Board.UNO_R3);
        assertThatThrownBy(() -> Board.fromId("arduino-one-q"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("arduino-one-q");
    }

    @Test
    @DisplayName("The Portenta H7 resolves to the Mbed M7 target")
    void shouldResolvePortentaH7Target() {
        Board board = Board.fromId("ARDUINO-PORTENTA-H7");

        assertThat(board).isEqualTo(Board.PORTENTA_H7);
        assertThat(board.annotationArgument()).isEqualTo("ArduinoPortentaH7Board.class");
        assertThat(board.fqbn()).isEqualTo("arduino:mbed_portenta:envie_m7");
        assertThat(board.core()).isEqualTo(ArduinoCore.MBED_PORTENTA);
        assertThat(Board.fromApiClassName("io/github/jabrena/juno/annotations/ArduinoPortentaH7Board"))
                .isEqualTo(board);
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
    @DisplayName("The AI Thinker ESP32-CAM exposes the ESP32 software capabilities")
    void shouldExposeTheEsp32CamSoftwareCapabilities() {
        Board board = Board.AI_THINKER_ESP32_CAM;

        assertThat(board.supports(Capability.WIFI_ACCESS_POINT)).isTrue();
        assertThat(board.supports(Capability.HTTPS_SERVER)).isTrue();
        assertThat(board.supports(Capability.TLS_EMAIL)).isTrue();
        assertThat(board.supports(Capability.SMTP_STARTTLS)).isTrue();
        assertThat(board.supports(Capability.WATCHDOG)).isTrue();
        assertThat(board.supports(Capability.UUID_RANDOM)).isTrue();
        assertThat(board.supports(Capability.SECURE_ENTROPY)).isTrue();
        assertThat(board.supports(Capability.DOUBLE_MATH)).isTrue();
        assertThat(board.supports(Capability.USB_HID)).isFalse();
        assertThat(board.supports(Capability.LED_MATRIX)).isFalse();
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
                import io.github.jabrena.juno.util.Delay;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoBlink {
                    public static void main(String[] args) {
                        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
                import io.github.jabrena.juno.hardware.gpio.Gpio;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoPins {
                    public static void main(String[] args) {
                        Gpio gpio = MCU.getGpio();
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.net.Wifi;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoWifi {
                    public static void main(String[] args) {
                        Wifi wifi = MCU.getWifi();
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.net.http.HttpServer;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoHttpServer {
                    public static void main(String[] args) {
                        MCU.getWifi();
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.net.BluetoothLe;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoBluetooth {
                    public static void main(String[] args) {
                        BluetoothLe bluetooth = MCU.getBluetoothLe();
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
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.hardware.hid.Mouse;
                @Board(RaspberryPico2WifiBoard.class)
                public final class PicoMouse {
                    public static void main(String[] args) {
                        Mouse mouse = MCU.getMouse();
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
                import io.github.jabrena.juno.hardware.MCU;
                @Board({RaspberryPico2WifiBoard.class, ArduinoUnoR4WiFiBoard.class})
                public final class PicoI2c {
                    public static void main(String[] args) {
                        MCU.getI2c().begin();
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
                import io.github.jabrena.juno.hardware.MCU;
                @Board(M5StackCardputerBoard.class)
                public final class CardputerI2c {
                    public static void main(String[] args) {
                        MCU.getI2c().begin();
                    }
                }
                """;
        String camSource = """
                package demo;
                import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                @Board(AiThinkerEsp32CamBoard.class)
                public final class CamI2c {
                    public static void main(String[] args) {
                        MCU.getI2c().begin();
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
                import io.github.jabrena.juno.hardware.MCU;
                @Board(AiThinkerEsp32CamBoard.class)
                public final class CamAndI2c {
                    public static void main(String[] args) {
                        MCU.getCamera();
                        MCU.getI2c().begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.CamAndI2c", source);
        JunoCompiler juno = new JunoCompiler();

        // When & Then
        assertThatThrownBy(() -> juno.compile(CompilerTestSupport.classpath(temporaryDirectory), "demo.CamAndI2c",
                false, Optional.of(Board.AI_THINKER_ESP32_CAM.id())))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("MCU.getCamera() and MCU.getI2c()")
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
    @DisplayName("Declaring multiple boards without a requested board fails")
    void shouldFailWhenMultipleBoardsAreDeclaredWithoutARequestedBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoPortentaH7Board.class, ArduinoUnoR4WiFiBoard.class})
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
                .hasMessageContaining("Portenta H7")
                .hasMessageContaining("UNO R4 WiFi");
    }

    @Test
    @DisplayName("A requested board picks one of the declared boards")
    void shouldPickARequestedBoardFromTheDeclaredBoards() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoPortentaH7Board.class, ArduinoUnoR4WiFiBoard.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);

        // When & Then
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.Portable",
                Optional.of(Board.PORTENTA_H7.id())).board()).isEqualTo(Board.PORTENTA_H7);
    }

    @Test
    @DisplayName("A requested board picks the R4 when it is one of the declared boards")
    void shouldPickTheR4WhenItIsARequestedDeclaredBoard() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board({ArduinoPortentaH7Board.class, ArduinoUnoR4WiFiBoard.class})
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
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                @Board(ArduinoPortentaH7Board.class)
                public final class OnlyPortenta {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnlyPortenta", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.OnlyPortenta",
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
                Optional.of(Board.PORTENTA_H7.id())).board()).isEqualTo(Board.PORTENTA_H7);
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
    @DisplayName("A Powered Up hub built on the Bluetooth LE radio is rejected on the UNO R3 at compile time")
    void shouldRejectTheBluetoothLeRadioOnTheUnoR3() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                @Board(ArduinoUnoR3Board.class)
                public final class NoRadio {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub();
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
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpPort;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub();
                        if (hub.connect(1000)) {
                            hub.getMotor(PoweredUpPort.A).drive(50);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableTrain", source);

        // When & Then
        // Both boards provide Bluetooth LE: the R4 WiFi through its ESP32-S3, the Portenta H7 through its Murata module.
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.PortableTrain",
                Optional.of(Board.PORTENTA_H7.id())).board()).isEqualTo(Board.PORTENTA_H7);
    }

    @Test
    @DisplayName("The Powered Up hub program also links for the R4 WiFi")
    void shouldLinkThePoweredUpHubProgramForTheR4WiFi() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpPort;
                import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        RemotePoweredUpHub hub = new RemotePoweredUpHub();
                        if (hub.connect(1000)) {
                            hub.getMotor(PoweredUpPort.A).drive(50);
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
    @DisplayName("Each board declares its Arduino core")
    void shouldDeclareEachBoardsArduinoCore() {
        // When & Then
        assertThat(new ArduinoCore[] {Board.UNO_R3.core(), Board.UNO_R4_WIFI.core(), Board.PORTENTA_H7.core()})
                .containsExactly(ArduinoCore.CLASSIC_AVR, ArduinoCore.RENESAS_UNO, ArduinoCore.MBED_PORTENTA);
    }

    @Test
    @DisplayName("The UNO R4 WiFi supports every general capability except Cardputer hardware, its IMU, PSRAM and the TLS server")
    void shouldSupportEveryGeneralCapabilityOnTheUnoR4WiFi() {
        // When & Then
        assertThat(Capability.values()).filteredOn(capability -> capability != Capability.M5_DISPLAY
                        && capability != Capability.M5_KEYBOARD && capability != Capability.M5_AUDIO
                        && capability != Capability.M5_BATTERY && capability != Capability.HTTPS_SERVER
                        && capability != Capability.CAMERA
                        && capability != Capability.HTTP_STREAM && capability != Capability.PSRAM
                        && capability != Capability.ONBOARD_SD_CARD)
                .allMatch(Board.UNO_R4_WIFI::supports);
    }

    @ParameterizedTest(name = "{0} supports {1}: {2}")
    @MethodSource("capabilities")
    @DisplayName("Each board declares its capabilities")
    void shouldDeclareEachBoardsCapabilities(Board board, Capability capability, boolean expected) {
        // When & Then
        assertThat(board.supports(capability)).isEqualTo(expected);
    }

    private CompilationResult compileUnoR4() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.util.Delay;
                import io.github.jabrena.juno.hardware.MCU;
                @Board(ArduinoUnoR4WiFiBoard.class)
                public final class OnUnoR4 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                        MCU.getDisplay();
                        int columns = MCU.getDisplay().width();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoR4", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoR4");
    }
}
