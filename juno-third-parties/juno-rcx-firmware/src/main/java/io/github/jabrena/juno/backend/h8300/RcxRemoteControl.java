package io.github.jabrena.juno.backend.h8300;

/**
 * The RCX Remote Control's commands in {@link RcxRemoteFirmware}: its button state (0xD2) and the message (0xF7), which
 * LEGO's firmware takes without a reply. The remote repeats its button state while a button is held and sends it once
 * more with no button on release: the motor and stop buttons act on every state ({@link RcxMotorCommands#remoteDrive}),
 * while the message and sound buttons act once per press. The message, set by 0xF7 or a message button, is polled as
 * source 15, as with LEGO's firmware.
 */
final class RcxRemoteControl {
    /** Command 0xD2: the button state, high byte then low byte. */
    static final String BUTTONS = "remote_buttons";
    /** Command 0xF7: the message, 0-255. */
    static final String SET_MESSAGE = "set_message";
    /** The poll of source 15: replies with the message. */
    static final String POLL_MESSAGE = "poll_message";

    /** The button state of the previous 0xD2, high byte first, so a press acts once however often it repeats. */
    private static final int PREVIOUS_BUTTONS = 0xC106;
    /** The last message, 0 until one arrives. */
    private static final int MESSAGE = 0xC108;
    /** Message buttons 1-3: low byte, bits 0x01, 0x02 and 0x04. */
    private static final int[] MESSAGE_BUTTONS = {0x01, 0x02, 0x04};
    /** The sound button: high byte, bit 0x80. */
    private static final int SOUND_BUTTON = 0x80;
    private static final int SOUND_BEEP_BEEP = 1;

    private final RcxCode code;
    private final H8Assembler assembler;
    private final RcxMotorCommands motors;

    RcxRemoteControl(RcxCode code, RcxMotorCommands motors) {
        this.code = code;
        this.assembler = code.assembler();
        this.motors = motors;
    }

    /** The command handlers and the message poll. */
    void handlers() {
        buttons();
        setMessage();
        pollMessage();
    }

    private void buttons() {
        assembler.label(BUTTONS);
        motors.remoteDrive(RcxCode.REQUEST + 1);
        for (int i = 0; i < MESSAGE_BUTTONS.length; i++) {
            String skip = code.newLabel();
            skipUnlessPressed(1, MESSAGE_BUTTONS[i], skip);
            assembler.moveImmediate(i + 1, H8Register.R0);
            assembler.store(H8Register.R0, MESSAGE);
            assembler.label(skip);
        }
        String silent = code.newLabel();
        skipUnlessPressed(0, SOUND_BUTTON, silent);
        code.rom().call(RcxRom.PLAY_SYSTEM_SOUND, RcxRom.SOUND_PLAY, SOUND_BEEP_BEEP);
        assembler.label(silent);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0H);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.store(H8Register.R0, PREVIOUS_BUTTONS);
        assembler.jump(RcxCode.NO_REPLY);
    }

    private void setMessage() {
        assembler.label(SET_MESSAGE);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.store(H8Register.R0, MESSAGE);
        assembler.jump(RcxCode.NO_REPLY);
    }

    private void pollMessage() {
        assembler.label(POLL_MESSAGE);
        assembler.load(MESSAGE, H8Register.R1);
        code.replyWithWord(H8Register.R1);
    }

    /**
     * Branches to {@code skip} unless the button {@code bit} in byte {@code offset} (0 high, 1 low) of the button state
     * is held now but was not in the previous state.
     */
    private void skipUnlessPressed(int offset, int bit, String skip) {
        assembler.loadByte(RcxCode.REQUEST + 1 + offset, H8ByteRegister.R0L);
        assembler.and(bit, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, skip);
        assembler.loadByte(PREVIOUS_BUTTONS + offset, H8ByteRegister.R0L);
        assembler.and(bit, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, skip);
    }
}
