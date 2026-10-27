package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.ArduinoCore;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
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
                arguments(Board.M5_CARDPUTER, Capability.BIG_NUMBERS, true),
                arguments(Board.M5_CARDPUTER, Capability.CLASS_INHERITANCE, true),
                arguments(Board.M5_CARDPUTER, Capability.USB_HID, false),
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
        assertThat(board.fqbn()).isEqualTo("m5stack:esp32:m5stack_cardputer");
        assertThat(board.core()).isEqualTo(ArduinoCore.ESP32);
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
    @DisplayName("A Powered Up hub program is portable across both boards")
    void shouldLinkAPoweredUpHubProgramForBothBoards() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        PoweredUpHubRemote hub = new PoweredUpHubRemote();
                        if (hub.connect(1000)) {
                            hub.setMotorPower(PoweredUpHubRemote.PORT_A, 50);
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
                import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpHubRemote;
                @Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class})
                public final class PortableTrain {
                    public static void main(String[] args) {
                        PoweredUpHubRemote hub = new PoweredUpHubRemote();
                        if (hub.connect(1000)) {
                            hub.setMotorPower(PoweredUpHubRemote.PORT_A, 50);
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
                import io.github.jabrena.juno.hardware.net.udp.Udp;
                import io.github.jabrena.juno.hardware.net.http.HttpClient;
                import io.github.jabrena.juno.hardware.net.email.Pop3Client;
                import io.github.jabrena.juno.hardware.net.email.Smtp;
                @Board(ArduinoUnoQBoard.class)
                public final class WifiOnUnoQ {
                    public static void main(String[] args) {
                        ArduinoUno.getWifi().status();
                        Udp.listen(5000);
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
    @DisplayName("The UNO R4 WiFi supports every capability except Cardputer hardware and the TLS server")
    void shouldSupportEveryGeneralCapabilityOnTheUnoR4WiFi() {
        // When & Then
        assertThat(Capability.values()).filteredOn(capability -> capability != Capability.M5_DISPLAY
                        && capability != Capability.M5_KEYBOARD && capability != Capability.M5_AUDIO
                        && capability != Capability.M5_BATTERY && capability != Capability.HTTPS_SERVER)
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
