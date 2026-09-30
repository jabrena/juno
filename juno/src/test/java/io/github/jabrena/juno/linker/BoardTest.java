package io.github.jabrena.juno.linker;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import io.github.jabrena.juno.board.ArduinoCore;
import io.github.jabrena.juno.board.Board;
import io.github.jabrena.juno.board.Capability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsToUnoR4WiFiWithoutAnAnnotation() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class Unannotated {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unannotated", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.Unannotated");

        assertThat(program.board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    void theUnoQRunsUnderTheZephyrCore() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoQ.class)
                public final class OnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                        Delay.micros(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoQ", source);
        Program program = CompilerTestSupport.link(temporaryDirectory, "demo.OnUnoQ");
        assertThat(program.board()).isEqualTo(Board.UNO_Q);
        assertThat(program.board().fqbn()).isEqualTo("arduino:zephyr:unoq");

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoQ");
        // Zephyr inlines delay()/delayMicroseconds() and provides yield() itself.
        assertThat(result.assembly()).contains("bl juno_delay\n", "bl juno_delay_microseconds\n")
                .doesNotContain("bl delay\n", "bl delayMicroseconds\n");
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_delay(uint32_t ms)")
                .doesNotContain("extern \"C\" void yield()", "Arduino_LED_Matrix", "ArduinoLEDMatrix");
    }

    @Test
    void theUnoR4KeepsItsOwnYieldAndLedMatrix() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class OnUnoR4 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnUnoR4", source);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.OnUnoR4");
        assertThat(result.assembly()).contains("bl delay\n").doesNotContain("juno_delay");
        assertThat(result.runtimeShim()).contains("extern \"C\" void yield()", "#include \"Arduino_LED_Matrix.h\"",
                "static ArduinoLEDMatrix juno_led_matrix;");
    }

    @Test
    void theUnoQHasNoWatchdog() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.Watchdog;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoQ.class)
                @Watchdog(timeoutMillis = 3000)
                public final class WatchdogOnUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WatchdogOnUnoQ", source);
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.WatchdogOnUnoQ"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("@Watchdog requires @Board(ArduinoUnoR4WiFi.class)");
    }

    @Test
    void declaringMultipleBoardsWithoutARequestedBoardFails() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Portable"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("@Board declares multiple boards")
                .hasMessageContaining("UNO Q")
                .hasMessageContaining("UNO R4 WiFi");
    }

    @Test
    void aRequestedBoardPicksOneOfTheDeclaredBoards() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
                public final class Portable {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Portable", source);

        Program onUnoQ = CompilerTestSupport.link(temporaryDirectory, "demo.Portable",
                Optional.of("arduino-uno-q"));
        assertThat(onUnoQ.board()).isEqualTo(Board.UNO_Q);

        Program onUnoR4 = CompilerTestSupport.link(temporaryDirectory, "demo.Portable",
                Optional.of("arduino-uno-r4-wifi"));
        assertThat(onUnoR4.board()).isEqualTo(Board.UNO_R4_WIFI);
    }

    @Test
    void aRequestedBoardNotDeclaredByBoardFails() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.Delay;
                @Board(ArduinoUnoQ.class)
                public final class OnlyUnoQ {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.OnlyUnoQ", source);
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.OnlyUnoQ",
                Optional.of("arduino-uno-r4-wifi")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("is not one of @Board's declared boards");
    }

    @Test
    void anUnknownRequestedBoardIdFails() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
                public final class Unannotated2 {
                    public static void main(String[] args) {
                        Delay.millis(1);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Unannotated2", source);
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.Unannotated2",
                Optional.of("arduino-mega")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("Unknown board 'arduino-mega'");
    }

    @Test
    void aPortableProgramMustSupportLedMatrixOnEveryDeclaredBoard() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.led.LedMatrix;
                @Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
                public final class PortableWithLedMatrix {
                    public static void main(String[] args) {
                        LedMatrix.begin();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PortableWithLedMatrix", source);
        // UNO R4 WiFi alone has an LED matrix; declaring UNO Q too must fail even though the build
        // requested here (UNO R4 WiFi) does support it, since the same source also claims UNO Q support.
        assertThatThrownBy(() -> CompilerTestSupport.link(temporaryDirectory, "demo.PortableWithLedMatrix",
                Optional.of("arduino-uno-r4-wifi")))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("LedMatrix requires @Board(ArduinoUnoR4WiFi.class)")
                .hasMessageContaining("UNO Q");
    }

    @Test
    void theUnoQSupportsPortableWifiUdpHttpsAndSecureEmail() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.net.Wifi;
                import io.github.jabrena.juno.api.io.net.Udp;
                import io.github.jabrena.juno.api.io.net.http.HttpsClient;
                import io.github.jabrena.juno.api.io.net.email.Pop3Client;
                import io.github.jabrena.juno.api.io.net.email.Smtp;
                @Board(ArduinoUnoQ.class)
                public final class WifiOnUnoQ {
                    public static void main(String[] args) {
                        Wifi.status();
                        Udp.listen(5000);
                        byte[] body = new byte[8];
                        byte[] headers = new byte[8];
                        int[] result = new int[2];
                        HttpsClient.get("example.com", 443, "/", body, 8, headers, 8, result);
                        Smtp.sendTls("mail.example.com", 465, "me@example.com", "secret",
                                "me@example.com", "me@example.com", "Hello", "Hello");
                        Pop3Client.messageCount("mail.example.com", 995, "me@example.com", "secret");
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WifiOnUnoQ", source);
        assertThat(CompilerTestSupport.link(temporaryDirectory, "demo.WifiOnUnoQ").board())
                .isEqualTo(Board.UNO_Q);
    }

    @Test
    void everyBoardDeclaresItsCoreAndCapabilities() {
        assertThat(Board.UNO_R4_WIFI.core()).isEqualTo(ArduinoCore.RENESAS_UNO);
        assertThat(Capability.values()).allMatch(Board.UNO_R4_WIFI::supports);
        assertThat(Board.UNO_Q.core()).isEqualTo(ArduinoCore.ZEPHYR);
        assertThat(Board.UNO_Q.supports(Capability.WIFI)).isTrue();
        assertThat(Board.UNO_Q.supports(Capability.HTTPS_CLIENT)).isTrue();
        assertThat(Board.UNO_Q.supports(Capability.LED_MATRIX)).isFalse();
        assertThat(Board.UNO_Q.supports(Capability.WIFI_S3_NETWORKING)).isFalse();
        assertThat(Board.UNO_Q.supports(Capability.WATCHDOG)).isFalse();
    }
}
