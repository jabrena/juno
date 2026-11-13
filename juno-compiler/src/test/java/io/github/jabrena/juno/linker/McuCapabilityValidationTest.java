package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reachable MCU getters are validated against every declared board")
class McuCapabilityValidationTest {

    @TempDir
    Path temporaryDirectory;

    private void compile(String className, String source) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, className, source);
    }

    @Nested
    @DisplayName("Rejected calls")
    class Rejected {
        @Test
        @DisplayName("An unsupported MCU.getWifi() names the getter, the board and the required annotation")
        void shouldRejectWifiOnTheUnoR3() throws Exception {
            compile("demo.R3Wifi", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoUnoR3Board.class)
                    public final class R3Wifi {
                        public static void main(String[] args) {
                            MCU.getWifi().begin("ssid", "password");
                        }
                    }
                    """);

            assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.R3Wifi", Optional.empty()))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("MCU.getWifi requires @Board(")
                    .hasMessageContaining("UNO R3 has no onboard Wi-Fi radio");
        }

        @Test
        @DisplayName("MCU.getSdCard() on a board without an onboard socket is rejected and names the boards that have one")
        void shouldRejectOnboardSdCardOnTheUnoR4() throws Exception {
            compile("demo.R4Sd", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoUnoR4WiFiBoard.class)
                    public final class R4Sd {
                        public static void main(String[] args) {
                            MCU.getSdCard().begin();
                        }
                    }
                    """);

            assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.R4Sd", Optional.empty()))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("MCU.getSdCard requires @Board(")
                    .hasMessageContaining("UNO R4 WiFi has no onboard SD-card socket");
        }

        @Test
        @DisplayName("Selecting a capable board cannot bypass an incapable declared board")
        void shouldRejectAMultiBoardProgramWhenAnyDeclaredBoardLacksTheGetter() throws Exception {
            compile("demo.MixedWifi", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoR3Board.class})
                    public final class MixedWifi {
                        public static void main(String[] args) {
                            MCU.getWifi();
                        }
                    }
                    """);

            assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.MixedWifi",
                    Optional.of(Board.UNO_R4_WIFI.id())))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("MCU.getWifi requires @Board(")
                    .hasMessageContaining("UNO R3");
        }
    }

    @Nested
    @DisplayName("Every capability-gated MCU getter is rejected on a board that lacks it")
    class EveryGatedGetter {
        /** Each MCU getter that needs a board capability, with the capability it requires. */
        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> gatedGetters() {
            return java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("getWifi", Capability.ONBOARD_WIFI, Board.UNO_R3),
                    org.junit.jupiter.params.provider.Arguments.of("getBluetoothLe", Capability.BLUETOOTH_LE, Board.UNO_R3_WIFI_REV2),
                    org.junit.jupiter.params.provider.Arguments.of("getMouse", Capability.USB_HID, Board.UNO_Q),
                    org.junit.jupiter.params.provider.Arguments.of("getLedMatrix", Capability.LED_MATRIX, Board.NANO_ESP32),
                    org.junit.jupiter.params.provider.Arguments.of("getAccelerometer", Capability.ONBOARD_IMU, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getGyroscope", Capability.ONBOARD_IMU, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getCamera", Capability.CAMERA, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getSdCard", Capability.ONBOARD_SD_CARD, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getDisplay", Capability.M5_DISPLAY, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getKeyboard", Capability.M5_KEYBOARD, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getAudio", Capability.M5_AUDIO, Board.UNO_R4_WIFI),
                    org.junit.jupiter.params.provider.Arguments.of("getBattery", Capability.M5_BATTERY, Board.UNO_R4_WIFI));
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "MCU.{0}() on {2} (no {1})")
        @org.junit.jupiter.params.provider.MethodSource("gatedGetters")
        @DisplayName("The compiler names the getter's capability and a board that lacks it")
        void shouldRejectTheGetterOnABoardWithoutItsCapability(String getter, Capability capability, Board board)
                throws Exception {
            // Given: a board from the table that lacks the capability
            assertThat(board.supports(capability)).isFalse();
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Gated", """
                    package demo;
                    @io.github.jabrena.juno.annotations.Board(%s)
                    public final class Gated {
                        public static void main(String[] args) {
                            io.github.jabrena.juno.hardware.MCU.%s();
                        }
                    }
                    """.formatted("io.github.jabrena.juno.annotations." + board.annotationArgument(), getter));

            // When & Then
            assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Gated", Optional.empty()))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining(capability.apiName() + " requires @Board(")
                    .hasMessageContaining(board.displayName());
        }
    }

    @Nested
    @DisplayName("Accepted calls")
    class Accepted {
        @Test
        @DisplayName("A capability-gated getter that no entry point reaches does not fail compilation")
        void shouldIgnoreAnUnreachableCapabilityGetter() throws Exception {
            compile("demo.Unreached", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board(ArduinoUnoR3Board.class)
                    public final class Unreached {
                        public static void main(String[] args) {
                        }

                        static void neverCalled() {
                            MCU.getWifi();
                        }
                    }
                    """);

            assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.Unreached", Optional.empty()).board())
                    .isEqualTo(Board.UNO_R3);
        }

        @Test
        @DisplayName("A program using only portable MCU getters links for every declared board")
        void shouldLinkPortableGettersForEveryDeclaredBoard() throws Exception {
            compile("demo.Portable", """
                    package demo;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
                    import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board({ArduinoUnoR3Board.class, ArduinoUnoR4WiFiBoard.class})
                    public final class Portable {
                        public static void main(String[] args) {
                            MCU.getGpio().pinMode(MCU.getGpio().digitalPin("D13"), 1);
                            MCU.getClock().millis();
                        }
                    }
                    """);

            for (Board board : new Board[] {Board.UNO_R3, Board.UNO_R4_WIFI}) {
                CompilationResult result = new JunoCompiler().compile(
                        CompilerTestSupport.classpath(temporaryDirectory), "demo.Portable", false,
                        Optional.of(board.id()));
                assertThat(result.fqbn()).isEqualTo(board.fqbn());
            }
        }

        @Test
        @DisplayName("MCU.getSdCard() links on the M5Stack Cardputer and the AI Thinker ESP32-CAM")
        void shouldLinkOnboardSdCardOnTheSdSocketBoards() throws Exception {
            compile("demo.SdBoards", """
                    package demo;
                    import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
                    import io.github.jabrena.juno.annotations.Board;
                    import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
                    import io.github.jabrena.juno.hardware.MCU;
                    @Board({M5StackCardputerBoard.class, AiThinkerEsp32CamBoard.class})
                    public final class SdBoards {
                        public static void main(String[] args) {
                            MCU.getSdCard().begin();
                        }
                    }
                    """);

            for (Board board : new Board[] {Board.M5_CARDPUTER, Board.AI_THINKER_ESP32_CAM}) {
                CompilationResult result = new JunoCompiler().compile(
                        CompilerTestSupport.classpath(temporaryDirectory), "demo.SdBoards", false,
                        Optional.of(board.id()));
                assertThat(result.fqbn()).isEqualTo(board.fqbn());
            }
        }
    }
}
