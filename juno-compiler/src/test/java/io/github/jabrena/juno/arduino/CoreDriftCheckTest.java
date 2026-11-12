package io.github.jabrena.juno.arduino;

import io.github.jabrena.juno.board.Board;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Core-drift check")
class CoreDriftCheckTest {

    private static final CoreBaseline UNO_R4 = new CoreBaseline(Board.UNO_R4_WIFI, "arduino:renesas_uno@1.6.0",
            6744, 327);

    @Test
    @DisplayName("Passes when the static RAM is unchanged")
    void shouldPassWhenUnchanged() {
        assertThat(CoreDriftCheck.evaluate(UNO_R4, new CoreDriftCheck.Measurement("1.6.0", 6744)).passes()).isTrue();
    }

    @Test
    @DisplayName("Passes at exactly the margin")
    void shouldPassAtExactlyTheMargin() {
        assertThat(CoreDriftCheck.evaluate(UNO_R4, new CoreDriftCheck.Measurement("1.7.0", 6744 + 327)).passes())
                .isTrue();
    }

    @Test
    @DisplayName("Fails one byte beyond the margin")
    void shouldFailOneByteBeyondTheMargin() {
        assertThat(CoreDriftCheck.evaluate(UNO_R4, new CoreDriftCheck.Measurement("1.7.0", 6744 + 328)).passes())
                .isFalse();
    }

    @Test
    @DisplayName("A core that frees RAM passes")
    void shouldPassWhenTheCoreFreesRam() {
        assertThat(CoreDriftCheck.evaluate(UNO_R4, new CoreDriftCheck.Measurement("1.7.0", 5000)).passes()).isTrue();
    }

    @Test
    @DisplayName("A failure names the board, both figures and both core versions")
    void shouldNameTheBoardFiguresAndCoreVersions() {
        // When
        CoreDriftCheck.Verdict verdict = CoreDriftCheck.evaluate(UNO_R4,
                new CoreDriftCheck.Measurement("1.7.0", 9000));

        // Then
        assertThat(verdict.passes()).isFalse();
        assertThat(verdict.message())
                .contains("arduino-uno-r4-wifi")
                .contains("from 6744 to 9000 bytes")
                .contains("arduino:renesas_uno@1.6.0")
                .contains("arduino:renesas_uno@1.7.0")
                .contains("327-byte margin")
                .contains("core-drift-baselines.txt");
    }

    @Test
    @DisplayName("A pass names the figures it compared")
    void shouldDescribeAPass() {
        assertThat(CoreDriftCheck.evaluate(UNO_R4, new CoreDriftCheck.Measurement("1.6.0", 6800)).message())
                .contains("+56 against the baseline of 6744 bytes");
    }

    @Test
    @DisplayName("The recorded baselines cover every board exactly once")
    void shouldRecordEveryBoardOnce() throws IOException {
        // When
        List<CoreBaseline> baselines = CoreBaseline.load();

        // Then
        assertThat(baselines).extracting(CoreBaseline::board).containsExactlyInAnyOrder(Board.values());
    }

    @ParameterizedTest(name = "{0} has a positive margin")
    @EnumSource(value = Board.class)
    @DisplayName("Every recorded board has a positive margin")
    void shouldGiveEveryBoardAPositiveMargin(Board board) throws IOException {
        assertThat(CoreBaseline.load()).filteredOn(baseline -> baseline.board() == board)
                .allSatisfy(baseline -> assertThat(baseline.marginBytes()).isPositive());
    }

    @Test
    @DisplayName("A baseline line with a missing column is rejected")
    void shouldRejectAMalformedLine() {
        assertThatThrownBy(() -> CoreBaseline.parse("arduino-uno-r3 arduino:avr@1.8.8 198"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
