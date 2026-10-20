package io.github.jabrena.juno.remote.lego.rcx;

/**
 * An RCX brick's display, from {@link RemoteRcx#getLcd()}. Both commands need Juno's own RCX firmware
 * ({@code RcxRemoteFirmware}); LEGO's firmware does not know them and does not answer.
 */
public final class RcxLcd {
    /** The smallest number {@link #showNumber} displays. */
    public static final int MIN_NUMBER = -9999;
    /** The largest number {@link #showNumber} displays. */
    public static final int MAX_NUMBER = 9999;

    /** The display icons for {@link #showIcon}, numbered as the RCX ROM numbers them. */
    public static final int ICON_STANDING = 0x06;
    public static final int ICON_WALKING = 0x07;
    public static final int ICON_SENSOR_1_VIEW = 0x08;
    public static final int ICON_SENSOR_1_PRESSED = 0x09;
    public static final int ICON_SENSOR_2_VIEW = 0x0A;
    public static final int ICON_SENSOR_2_PRESSED = 0x0B;
    public static final int ICON_SENSOR_3_VIEW = 0x0C;
    public static final int ICON_SENSOR_3_PRESSED = 0x0D;
    public static final int ICON_MOTOR_A_VIEW = 0x0E;
    public static final int ICON_MOTOR_A_BACKWARD = 0x0F;
    public static final int ICON_MOTOR_A_FORWARD = 0x10;
    public static final int ICON_MOTOR_B_VIEW = 0x11;
    public static final int ICON_MOTOR_B_BACKWARD = 0x12;
    public static final int ICON_MOTOR_B_FORWARD = 0x13;
    public static final int ICON_MOTOR_C_VIEW = 0x14;
    public static final int ICON_MOTOR_C_BACKWARD = 0x15;
    public static final int ICON_MOTOR_C_FORWARD = 0x16;
    public static final int ICON_DATALOG = 0x18;
    public static final int ICON_DOWNLOAD = 0x19;
    public static final int ICON_UPLOAD = 0x1A;
    public static final int ICON_BATTERY_LOW = 0x1B;
    public static final int ICON_RANGE_SHORT = 0x1C;
    public static final int ICON_RANGE_LONG = 0x1D;
    /** Every segment of the display at once. */
    public static final int ICON_ALL = 0x20;

    private final RcxBrick brick;

    RcxLcd(RcxBrick brick) {
        this.brick = brick;
    }

    /**
     * Shows {@code value}, limited to {@link #MIN_NUMBER}..{@link #MAX_NUMBER}, in place of {@code JUno} until the
     * next call. Returns whether the brick acknowledged.
     */
    public boolean showNumber(int value) {
        return brick.showNumber(value);
    }

    /**
     * Lights the icon {@code icon}, {@code ICON_*}, or with {@code lit} false hands it back to the brick, which lights
     * some icons from its own state: running motors' arrows, pressed touch sensors, low battery, and the minifigure.
     * Returns whether the brick acknowledged.
     */
    public boolean showIcon(int icon, boolean lit) {
        return brick.showIcon(icon, lit);
    }
}
