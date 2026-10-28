package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/** The five-channel selector shown beside the RCX's standing minifigure. */
final class RcxChannel {
    /** Free word after the button and text state; the motor powers occupy 0xC056-0xC05B. */
    static final int ADDRESS = 0xC116;
    static final int FIRST = 1;
    static final int LAST = 5;
    static final String START = "start_channel";
    static final String NEXT = "next_channel";
    static final String DRAW = "draw_channel";

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxChannel(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    String nextLabel() {
        return NEXT;
    }

    /** The startup and {@link #NEXT} subroutines. */
    void handlers() {
        start();
        next();
        draw();
    }

    private void start() {
        assembler.label(START);
        assembler.moveImmediate(encoded(FIRST), H8Register.R0);
        assembler.store(H8Register.R0, ADDRESS);
        display();
        assembler.returnFromSubroutine();
    }

    private void next() {
        assembler.label(NEXT);
        assembler.load(ADDRESS, H8Register.R0);
        assembler.moveImmediate(0x0110, H8Register.R1);
        assembler.add(H8Register.R1, H8Register.R0);
        assembler.moveImmediate(encoded(LAST + 1), H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        String keep = code.newLabel();
        assembler.branch(H8Condition.CARRY_SET, keep);
        assembler.moveImmediate(encoded(FIRST), H8Register.R0);
        assembler.label(keep);
        assembler.store(H8Register.R0, ADDRESS);
        display();
        assembler.returnFromSubroutine();
    }

    private void display() {
        assembler.call(DRAW);
        code.rom().call(RcxRom.REFRESH_DISPLAY);
    }

    private void draw() {
        assembler.label(DRAW);
        assembler.loadByte(ADDRESS, H8ByteRegister.R1L);
        assembler.moveImmediate(0, H8ByteRegister.R1H);
        code.rom().call(RcxRom.SET_LCD_NUMBER, Argument.constant(RcxRom.LCD_PROGRAM), Argument.register(H8Register.R1),
                Argument.constant(RcxRom.LCD_NO_COMMA));
        assembler.returnFromSubroutine();
    }

    static int encoded(int channel) {
        return (channel << 8) | (0xA3 + ((channel - 1) << 4));
    }
}
