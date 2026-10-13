package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Renders flash, RAM-arena and SD-card occupancy as segmented bars, redrawing a row only when it changes. */
final class TftDashboardStorageView implements TftDashboardScreen {
    private static final int ROW_FLASH = 0;
    private static final int ROW_RAM = 1;
    private static final int ROW_SD = 2;
    private static final int ROWS = 3;
    private static final int UNKNOWN = -1;

    private static final int CAPACITY_SCANNING = 0;
    private static final int CAPACITY_READY = 1;
    private static final int CAPACITY_UNAVAILABLE = 2;

    private static final int ROW_X = 12;
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_HEIGHT = 52;
    private static final int ROW_WIDTH = 296;
    private static final int BAR_OFFSET_Y = 20;
    private static final int BAR_HEIGHT = 16;
    private static final int SEGMENTS = 20;
    private static final int SEGMENT_STEP = 14;
    private static final int SEGMENT_WIDTH = 12;
    private static final int DETAIL_OFFSET_Y = 40;
    private static final int WARNING_PERCENT = 60;
    private static final int CRITICAL_PERCENT = 85;
    /** RGB565 (24, 32, 64): the unlit segments, a dim blue that reads as an empty track on navy. */
    private static final int TRACK = 0x1908;

    private static final int HOME_BUTTON_X = 196;
    private static final int HOME_BUTTON_Y = 200;
    private static final int HOME_BUTTON_WIDTH = 112;
    private static final int HOME_BUTTON_HEIGHT = 34;

    private final TftDashboardScreen home;
    private final int[] shownUsed = new int[ROWS];
    private final int[] shownCapacity = new int[ROWS];

    // Written by the background thread, read by the display loop.
    private volatile int sdCapacityState = CAPACITY_SCANNING;
    private volatile int sdTotalKiB = UNKNOWN;
    private volatile int sdFreeKiB = UNKNOWN;

    TftDashboardStorageView(TftDashboardScreen home) {
        this.home = home;
    }

    /** No card: the SD row shows "no card" instead of waiting for a scan. */
    void markSdUnavailable() {
        sdCapacityState = CAPACITY_UNAVAILABLE;
    }

    /** Runs on the background thread: counting the free space can read the card's whole allocation table. */
    void scanSdCapacity() {
        int total = SdCard.totalKiB();
        int free = SdCard.freeKiB();
        sdTotalKiB = total;
        sdFreeKiB = free;
        sdCapacityState = total > 0 && free >= 0 ? CAPACITY_READY : CAPACITY_UNAVAILABLE;
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 12);
        TftTouchShield.print("STORAGE");
        TftTouchShield.drawHorizontalLine(12, 34, 296, TftTouchShield.CYAN);
        drawHomeButton();

        for (int row = 0; row < ROWS; row++) {
            shownUsed[row] = Integer.MIN_VALUE;
            shownCapacity[row] = Integer.MIN_VALUE;
        }
        updateRow(ROW_FLASH, Memory.flashUsedBytes(), Memory.flashCapacityBytes());
    }

    @Override
    public void draw() {
        updateRow(ROW_RAM, Memory.arenaUsedBytes(), Memory.arenaCapacityBytes());
        updateSdRow();
    }

    @Override
    public TftDashboardScreen touch(int x, int y) {
        if (homeButtonContains(x, y)) {
            return home;
        }
        return this;
    }

    private void drawHomeButton() {
        TftTouchShield.fillRect(HOME_BUTTON_X, HOME_BUTTON_Y, HOME_BUTTON_WIDTH, HOME_BUTTON_HEIGHT,
                TftTouchShield.GRAY);
        TftTouchShield.drawRect(HOME_BUTTON_X, HOME_BUTTON_Y, HOME_BUTTON_WIDTH, HOME_BUTTON_HEIGHT,
                TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.GRAY);
        TftTouchShield.setCursor(228, 209);
        TftTouchShield.print("HOME");
    }

    private boolean homeButtonContains(int x, int y) {
        return x >= HOME_BUTTON_X
                && x < HOME_BUTTON_X + HOME_BUTTON_WIDTH
                && y >= HOME_BUTTON_Y
                && y < HOME_BUTTON_Y + HOME_BUTTON_HEIGHT;
    }

    private void updateSdRow() {
        int state = sdCapacityState;
        if (state == CAPACITY_READY) {
            int totalKiB = sdTotalKiB;
            updateRow(ROW_SD, totalKiB - sdFreeKiB, totalKiB);
        } else {
            // A capacity of UNKNOWN marks the row as pending (used 0) or unavailable (used UNKNOWN).
            int used = state == CAPACITY_UNAVAILABLE ? UNKNOWN : 0;
            updateRow(ROW_SD, used, UNKNOWN);
        }
    }

    private void updateRow(int row, int used, int capacity) {
        if (shownUsed[row] == used && shownCapacity[row] == capacity) {
            return;
        }
        shownUsed[row] = used;
        shownCapacity[row] = capacity;

        int y = FIRST_ROW_Y + row * ROW_HEIGHT;
        TftTouchShield.fillRect(ROW_X, y, ROW_WIDTH, ROW_HEIGHT - 4, TftTouchShield.NAVY);
        drawLabel(row, y);
        if (capacity <= 0 || used < 0) {
            drawBar(y, 0, TftTouchShield.GRAY);
            drawUnavailable(row, used, y);
            return;
        }

        int percent = percent(used, capacity);
        int color = levelColor(percent);
        drawPercent(percent, color, y);
        drawBar(y, percent, color);
        drawDetail(row, used, capacity, y);
    }

    private void drawLabel(int row, int y) {
        TftTouchShield.fillCircle(ROW_X + 5, y + 7, 5, accentColor(row));
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(ROW_X + 16, y);
        if (row == ROW_FLASH) {
            TftTouchShield.print("FLASH");
        } else if (row == ROW_RAM) {
            TftTouchShield.print("RAM ARENA");
        } else {
            TftTouchShield.print("SD CARD");
        }
    }

    private void drawPercent(int percent, int color, int y) {
        int digits = percent >= 100 ? 3 : percent >= 10 ? 2 : 1;
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TftTouchShield.NAVY);
        TftTouchShield.setCursor(ROW_X + ROW_WIDTH - (digits + 1) * 12, y);
        TftTouchShield.print(percent);
        TftTouchShield.print("%");
    }

    private void drawBar(int y, int percent, int color) {
        int lit = (percent * SEGMENTS + 99) / 100;
        int barY = y + BAR_OFFSET_Y;
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int x = ROW_X + segment * SEGMENT_STEP + (ROW_WIDTH - SEGMENTS * SEGMENT_STEP) / 2;
            TftTouchShield.fillRect(x, barY, SEGMENT_WIDTH, BAR_HEIGHT, segment < lit ? color : TRACK);
        }
    }

    private void drawDetail(int row, int used, int capacity, int y) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(ROW_X + 4, y + DETAIL_OFFSET_Y);
        if (row == ROW_FLASH) {
            TftTouchShield.print(used / 1024);
            TftTouchShield.print(" / ");
            TftTouchShield.print(capacity / 1024);
            TftTouchShield.print(" KiB used");
        } else if (row == ROW_RAM) {
            TftTouchShield.print(used);
            TftTouchShield.print(" / ");
            TftTouchShield.print(capacity);
            TftTouchShield.print(" B used");
        } else {
            TftTouchShield.print(used / 1024);
            TftTouchShield.print(" / ");
            TftTouchShield.print(capacity / 1024);
            TftTouchShield.print(" MiB used");
        }
    }

    private void drawUnavailable(int row, int used, int y) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.NAVY);
        TftTouchShield.setCursor(ROW_X + 4, y + DETAIL_OFFSET_Y);
        if (row == ROW_SD && used == 0) {
            TftTouchShield.print("scanning card...");
        } else if (row == ROW_SD) {
            TftTouchShield.print("no card");
        } else {
            TftTouchShield.print("not available");
        }
    }

    /** {@code used} as a share of {@code capacity}, 0..100, without overflowing on an SD card's KiB count. */
    private static int percent(int used, int capacity) {
        int share = capacity > 20_000_000 ? used / (capacity / 100) : used * 100 / capacity;
        return share < 0 ? 0 : share > 100 ? 100 : share;
    }

    private static int levelColor(int percent) {
        if (percent >= CRITICAL_PERCENT) {
            return TftTouchShield.RED;
        }
        if (percent >= WARNING_PERCENT) {
            return TftTouchShield.YELLOW;
        }
        return TftTouchShield.GREEN;
    }

    private static int accentColor(int row) {
        if (row == ROW_FLASH) {
            return TftTouchShield.MAGENTA;
        }
        if (row == ROW_RAM) {
            return TftTouchShield.CYAN;
        }
        return TftTouchShield.ORANGE;
    }
}
