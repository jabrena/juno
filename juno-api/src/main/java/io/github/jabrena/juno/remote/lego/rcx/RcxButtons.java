package io.github.jabrena.juno.remote.lego.rcx;

/**
 * An RCX brick's own View, Prgm and Run buttons, from {@link RemoteRcx#getButtons()}. Needs Juno's own RCX firmware
 * ({@code RcxRemoteFirmware}), which shows each clicked button's name on the brick's display and remembers the clicks
 * until {@link #clicked} reads them; LEGO's firmware does not answer.
 */
public final class RcxButtons {
    /** {@link #clicked} bits. */
    public static final int RUN = 0x01;
    public static final int VIEW = 0x02;
    public static final int PRGM = 0x04;

    private final RcxBrick brick;

    RcxButtons(RcxBrick brick) {
        this.brick = brick;
    }

    /**
     * The buttons clicked since the previous call, as a mask of {@link #RUN}, {@link #VIEW} and {@link #PRGM}
     * ({@code 0} for none), or {@code -1} if the brick did not answer. A request and a reply, which needs the IR
     * receiver wired.
     */
    public int clicked() {
        return brick.clickedButtons();
    }
}
