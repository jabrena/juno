package io.github.jabrena.juno.backend.h8300;

/**
 * What every part of {@link RcxRemoteFirmware} assembles with: the assembler, the ROM calls, unique labels, and the
 * message buffers with the shared entry points for replying. A command handler reads its arguments from
 * {@link #REQUEST} and ends with {@link #replyWith}, or a jump to {@link #REPLY_ACK} or {@link #NO_REPLY}.
 */
final class RcxCode {
    /** The received message: opcode, arguments, checksum. */
    static final int REQUEST = 0xC060;
    static final int REQUEST_CAPACITY = 16;
    /** The reply: complemented opcode, then its arguments. */
    static final int REPLY = 0xC070;
    /** Replies with the opcode alone. */
    static final String REPLY_ACK = "reply_ack";
    /** Replies with the number of bytes in r0. */
    static final String SEND_REPLY = "send_reply";
    /** Redraws the display and goes back to the main loop without replying, for messages LEGO's firmware never answers. */
    static final String NO_REPLY = "no_reply";
    /** The main loop, for messages that get no reply. */
    static final String LOOP = "loop";
    /** Subroutine: draws the display's number, or {@code JUno} before the first command, without refreshing. */
    static final String DRAW_NUMBER = "draw_number";

    private final H8Assembler assembler;
    private final RcxRom rom;
    private int labels;

    RcxCode(H8Assembler assembler) {
        this.assembler = assembler;
        this.rom = new RcxRom(assembler);
    }

    H8Assembler assembler() {
        return assembler;
    }

    RcxRom rom() {
        return rom;
    }

    String newLabel() {
        return "l" + labels++;
    }

    /** A conditional jump to anywhere: a short branch around an absolute jump. */
    void jumpIf(H8Condition condition, String target) {
        String skip = newLabel();
        assembler.branch(condition.opposite(), skip);
        assembler.jump(target);
        assembler.label(skip);
    }

    /** Sends a reply of {@code length} bytes from {@link #REPLY}. */
    void replyWith(int length) {
        assembler.moveImmediate(length, H8Register.R0);
        assembler.jump(SEND_REPLY);
    }

    /** Replies with the 16-bit word in {@code value} as two arguments, low byte first. */
    void replyWithWord(H8Register value) {
        assembler.storeByte(H8ByteRegister.low(value), REPLY + 1);
        assembler.storeByte(H8ByteRegister.high(value), REPLY + 2);
        replyWith(3);
    }

    void clearWord(int address) {
        assembler.moveImmediate(0, H8Register.R6);
        assembler.store(H8Register.R6, address);
    }
}
