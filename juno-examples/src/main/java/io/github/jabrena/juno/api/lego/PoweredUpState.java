package io.github.jabrena.juno.api.lego;

/** The shared state and layout constants of {@link PoweredUpHubTFT} and its helper classes. */
final class PoweredUpState {
    static final int PORTS = 4;
    static final int POWER_STEP = 20;
    static final int CONNECT_MILLIS = 10000;
    static final int TOUCH_LOCKOUT_MILLIS = 250;
    static final int REFRESH_MILLIS = 500;

    static final int VIEW_INFO = 0;
    static final int VIEW_LED = 1;
    static final int VIEW_MOTORS = 2;
    static final int VIEW_PAIR = 3;

    static final int STOP_COAST = 0;
    static final int STOP_BRAKE = 1;
    static final int STOP_HOLD = 2;

    static final int WIDTH = 240;
    static final int MARGIN = 5;
    static final int HEADER_HEIGHT = 24;
    static final int TAB_Y = 26;
    static final int TAB_HEIGHT = 32;
    static final int TAB_WIDTH = 60;
    static final int CONTENT_Y = 62;
    static final int CONTENT_HEIGHT = 218;
    static final int BOTTOM_Y = 284;
    static final int BOTTOM_HEIGHT = 32;

    static final int SMALL_BUTTON_WIDTH = 50;
    static final int WIDE_BUTTON_WIDTH = 110;
    static final int ROW_FIRST = 66;
    static final int ROW_PITCH = 40;
    static final int ROW_STOP = 232;
    static final int BUTTON_HEIGHT_MOTOR = 34;
    static final int PAIR_ROW_SYNC = 66;
    static final int PAIR_ROW_A = 106;
    static final int PAIR_ROW_B = 146;
    static final int ROW_LEVELS = 190;
    static final int LEVEL_WIDTH = 72;
    static final int LEVEL_GAP = 4;

    static final int SWATCH_WIDTH = 70;
    static final int SWATCH_HEIGHT = 60;
    static final int SWATCH_GAP = 10;
    static final int SWATCH_TOP = 70;

    int view;
    int ledIndex;
    final int[] power = new int[PORTS];
    final int[] zero = new int[PORTS];
    final int[] device = new int[PORTS];
    int hubType;
    /** The two ports of the pair (the first two motors found), or -1 while there are fewer than two. */
    int pairA = -1;
    int pairB = -1;
    boolean pairSync;
    /** The virtual port of the linked pair, or -1 when not linked. */
    int link = -1;
    int stopLevel = STOP_BRAKE;
}
