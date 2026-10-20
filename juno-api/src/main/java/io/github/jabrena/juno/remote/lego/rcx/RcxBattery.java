package io.github.jabrena.juno.remote.lego.rcx;

/** An RCX brick's batteries, from {@link RemoteRcx#getBattery()}. */
public final class RcxBattery {
    private final RcxBrick brick;

    RcxBattery(RcxBrick brick) {
        this.brick = brick;
    }

    /**
     * The battery voltage in millivolts (about 9000 with fresh batteries, and the brick warns below about 6000), or
     * {@code -1} if the brick did not answer. It is a request and a reply, which needs the IR receiver wired.
     */
    public int millivolts() {
        return brick.batteryMillivolts();
    }
}
