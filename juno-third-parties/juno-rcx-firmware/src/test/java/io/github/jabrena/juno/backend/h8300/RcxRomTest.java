package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RCX ROM calls")
class RcxRomTest {

    @Test
    @DisplayName("Passes ROM arguments in R6 and on the stack, last first")
    void shouldPassRomArgumentsInR6AndOnTheStackLastFirst() {
        // Given
        H8Assembler assembler = new H8Assembler(0x8000);
        assembler.label("start");

        // When
        new RcxRom(assembler).call(RcxRom.SET_LCD_NUMBER, RcxRom.LCD_UNSIGNED, 2026, RcxRom.LCD_NO_COMMA);

        // Then
        assertThat(HexFormat.of().formatHex(assembler.assemble("start").bytes())).isEqualTo(
                "79063002" + "6df6" + "790607ea" + "6df6" + "7906301f" + "5e001ff2" + "0b87" + "0b87");
    }

    @Test
    @DisplayName("Passes register arguments through R6 and the stack")
    void shouldPassRegisterArgumentsThroughR6AndTheStack() {
        // Given
        H8Assembler assembler = new H8Assembler(0x8000);
        assembler.label("start");

        // When
        new RcxRom(assembler).call(RcxRom.SET_LCD_NUMBER, RcxRom.Argument.register(H8Register.R1),
                RcxRom.Argument.register(H8Register.R0));

        // Then
        assertThat(assembler.assemble("start").bytes()).isNotEmpty();
    }

    @Test
    @DisplayName("Refuses to stack R6 because it carries the first argument")
    void shouldRefuseToStackR6BecauseItCarriesTheFirstArgument() {
        // Given
        H8Assembler assembler = new H8Assembler(0x8000);

        // When & Then
        assertThatThrownBy(() -> new RcxRom(assembler).call(RcxRom.SET_LCD_NUMBER,
                RcxRom.Argument.constant(1), RcxRom.Argument.register(H8Register.R6)))
                .isInstanceOf(FirmwareGenerationException.class)
                .hasMessageContaining("r6");
    }
}
