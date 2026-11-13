package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gpio.pulseIn: the core times the pulse")
class GpioPulseInTest {
    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "ArduinoUnoR3Board      | MCU       | pulseIn(static_cast<uint8_t>(pin),",
        "ArduinoUnoR4WiFiBoard  | MCU       | pulseIn(static_cast<uint8_t>(pin),",
        "ArduinoUnoQBoard       | MCU       | pulseIn(static_cast<uint8_t>(pin),",
        "ArduinoNanoEsp32Board  | MCU       | pulseIn(juno_nano_pin(pin),"
    })
    @DisplayName("A program that times a pulse calls juno_pulse_in, which the shim defines over the core's pulseIn")
    void shouldLowerPulseInToTheCoreHelper(String board, String facade, String coreCall) throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.hardware.%2$s;
                import io.github.jabrena.juno.util.Delay;
                @Board(%1$s.class)
                public final class PulseDemo {
                    public static void main(String[] args) {
                        int echoMicros = %2$s.getGpio().pulseIn(2, true, 30_000);
                        Delay.millis(echoMicros);
                    }
                }
                """.formatted(board, facade);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.PulseDemo", source);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.PulseDemo");

        // Then
        assertThat(result.assembly()).contains("juno_pulse_in");
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_pulse_in(int32_t pin, int32_t high,",
                coreCall, "high != 0 ? HIGH : LOW");
    }

    @Test
    @DisplayName("A program that times no pulse gets no pulseIn helper")
    void shouldLeaveThePulseInHelperOutWhenUnused() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.MCU;
                @Board(ArduinoNanoEsp32Board.class)
                public final class NoPulseDemo {
                    public static void main(String[] args) {
                        MCU.getGpio().digitalWrite(2, MCU.getGpio().digitalRead(3));
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.NoPulseDemo", source);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.NoPulseDemo");

        // Then
        assertThat(result.runtimeShim()).doesNotContain("juno_pulse_in", "pulseIn(");
    }
}
