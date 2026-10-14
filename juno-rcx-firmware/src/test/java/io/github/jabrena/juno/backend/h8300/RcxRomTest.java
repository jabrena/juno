package io.github.jabrena.juno.backend.h8300;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RcxRomTest {

    @Test
    void passesRomArgumentsInR6AndOnTheStackLastFirst() {
        H8Assembler assembler = new H8Assembler(0x8000);
        assembler.label("start");

        new RcxRom(assembler).call(RcxRom.SET_LCD_NUMBER, RcxRom.LCD_UNSIGNED, 2026, RcxRom.LCD_NO_COMMA);

        assertThat(HexFormat.of().formatHex(assembler.assemble("start").bytes())).isEqualTo(
                "79063002" + "6df6" + "790607ea" + "6df6" + "7906301f" + "5e001ff2" + "0b87" + "0b87");
    }
}
