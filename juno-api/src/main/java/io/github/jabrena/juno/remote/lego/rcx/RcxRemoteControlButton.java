package io.github.jabrena.juno.remote.lego.rcx;

/** One of the buttons exposed by the LEGO Mindstorms 9738 RCX remote control. */
public enum RcxRemoteControlButton {
    NONE(0),
    MESSAGE_1(0x0001),
    MESSAGE_2(0x0002),
    MESSAGE_3(0x0004),
    A_UP(0x0008),
    B_UP(0x0010),
    C_UP(0x0020),
    A_DOWN(0x0040),
    B_DOWN(0x0080),
    C_DOWN(0x0100),
    PROGRAM_1(0x0200),
    PROGRAM_2(0x0400),
    PROGRAM_3(0x0800),
    PROGRAM_4(0x1000),
    PROGRAM_5(0x2000),
    STOP(0x4000),
    SOUND(0x8000),
    UNKNOWN(-1);

    private final int mask;

    RcxRemoteControlButton(int mask) {
        this.mask = mask;
    }

    /** The bit used by this button in the RCX handset's 16-bit button mask. */
    public int mask() {
        return mask;
    }

    /** Whether this button is present in a raw handset mask. */
    public boolean isPressedIn(int buttons) {
        return this != NONE && this != UNKNOWN && (buttons & mask) != 0;
    }

    /** A stable console label for diagnostics without relying on enum reflection or allocation. */
    public String label() {
        return switch (this) {
            case NONE -> "NONE";
            case MESSAGE_1 -> "MESSAGE_1";
            case MESSAGE_2 -> "MESSAGE_2";
            case MESSAGE_3 -> "MESSAGE_3";
            case A_UP -> "A_UP";
            case B_UP -> "B_UP";
            case C_UP -> "C_UP";
            case A_DOWN -> "A_DOWN";
            case B_DOWN -> "B_DOWN";
            case C_DOWN -> "C_DOWN";
            case PROGRAM_1 -> "PROGRAM_1";
            case PROGRAM_2 -> "PROGRAM_2";
            case PROGRAM_3 -> "PROGRAM_3";
            case PROGRAM_4 -> "PROGRAM_4";
            case PROGRAM_5 -> "PROGRAM_5";
            case STOP -> "STOP";
            case SOUND -> "SOUND";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    /** Maps one raw button mask to its button; combinations and unsupported values become {@link #UNKNOWN}. */
    public static RcxRemoteControlButton fromMask(int buttons) {
        return switch (buttons) {
            case 0 -> NONE;
            case 0x0001 -> MESSAGE_1;
            case 0x0002 -> MESSAGE_2;
            case 0x0004 -> MESSAGE_3;
            case 0x0008 -> A_UP;
            case 0x0010 -> B_UP;
            case 0x0020 -> C_UP;
            case 0x0040 -> A_DOWN;
            case 0x0080 -> B_DOWN;
            case 0x0100 -> C_DOWN;
            case 0x0200 -> PROGRAM_1;
            case 0x0400 -> PROGRAM_2;
            case 0x0800 -> PROGRAM_3;
            case 0x1000 -> PROGRAM_4;
            case 0x2000 -> PROGRAM_5;
            case 0x4000 -> STOP;
            case 0x8000 -> SOUND;
            default -> UNKNOWN;
        };
    }
}
