package io.github.jabrena.juno.api.hardware.device.lcd;

import io.github.jabrena.juno.CompilationRequest;
import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.JunoCompiler;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LCD Keypad Shield with Wi-Fi")
class LcdKeypadWifiShieldConflictCompilationTest {
    private static final String MAIN = "io.github.jabrena.juno.api.hardware.device.lcd.LcdKeypadWifiShieldConflict";

    @Test
    @DisplayName("Does not compile for the UNO R3, whose WiFi Shield shares D4, D7 and D10 with the LCD")
    void shouldNotCompileForTheUnoR3() {
        // When & Then
        assertThatThrownBy(() -> compileFor(Board.UNO_R3))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("LcdKeypadShield cannot be used with the Arduino WiFi Shield on UNO R3")
                .hasMessageContaining("D4, D7 and D10");
    }

    @Test
    @DisplayName("Compiles for the UNO R4 WiFi, whose radio does not use those pins")
    void shouldCompileForTheUnoR4Wifi() throws Exception {
        // When
        CompilationResult result = compileFor(Board.UNO_R4_WIFI);

        // Then
        assertThat(result.assembly()).contains("juno_wifi_begin");
    }

    private static CompilationResult compileFor(Board board) throws URISyntaxException {
        Path examples = Path.of("target/classes");
        Path api = Path.of(Watchdog.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new JunoCompiler().compile(new CompilationRequest(List.of(examples, api), MAIN, false,
                Optional.of(board.id())));
    }
}
