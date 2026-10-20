package io.github.jabrena.juno.backend.h8300;

/**
 * The brick's own View, Prgm and Run buttons in {@link RcxRemoteFirmware}. The main loop reads them through the ROM
 * ({@link #CHECK}) and remembers each click, changing nothing on the brick, until the Arduino polls Juno's own source
 * {@link #SOURCE_CLICKED}, which replies with the clicks since the previous poll and forgets them.
 */
final class RcxBrickButtons {
    /** Subroutine: reads the buttons and remembers new clicks. */
    static final String CHECK = "check_brick_buttons";
    /** The poll of {@link #SOURCE_CLICKED}: replies with the clicks since the previous one and forgets them. */
    static final String POLL_CLICKED = "poll_clicked";
    /** Juno's own poll source for the clicked buttons; LEGO's firmware has no such source. */
    static final int SOURCE_CLICKED = 0x60;

    /** {@code read_buttons} bits, as leJOS's {@code Button.readButtons} reports them. */
    static final int RUN = 0x01;
    static final int VIEW = 0x02;
    static final int PRGM = 0x04;

    /** The buttons held at the last read, as {@code read_buttons} stores them. */
    private static final int HELD = 0xC10A;
    private static final int PREVIOUSLY_HELD = 0xC10C;
    /** The buttons clicked since the Arduino's last poll. */
    private static final int CLICKED = 0xC10E;
    private static final int[] BUTTONS = {RUN, VIEW, PRGM};

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxBrickButtons(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** The {@link #CHECK} subroutine and the poll handler. */
    void handlers() {
        check();
        pollClicked();
    }

    private void check() {
        assembler.label(CHECK);
        code.rom().call(RcxRom.READ_BUTTONS, RcxRom.BUTTONS_CODE, HELD);
        for (int button : BUTTONS) {
            String skip = code.newLabel();
            assembler.loadByte(HELD + 1, H8ByteRegister.R0L);
            assembler.and(button, H8ByteRegister.R0L);
            assembler.branch(H8Condition.EQUAL, skip);
            assembler.loadByte(PREVIOUSLY_HELD + 1, H8ByteRegister.R0L);
            assembler.and(button, H8ByteRegister.R0L);
            assembler.branch(H8Condition.NOT_EQUAL, skip);
            assembler.loadByte(CLICKED + 1, H8ByteRegister.R0L);
            assembler.or(button, H8ByteRegister.R0L);
            assembler.storeByte(H8ByteRegister.R0L, CLICKED + 1);
            assembler.label(skip);
        }
        assembler.load(HELD, H8Register.R0);
        assembler.store(H8Register.R0, PREVIOUSLY_HELD);
        assembler.returnFromSubroutine();
    }

    private void pollClicked() {
        assembler.label(POLL_CLICKED);
        assembler.load(CLICKED, H8Register.R1);
        code.clearWord(CLICKED);
        code.replyWithWord(H8Register.R1);
    }
}
