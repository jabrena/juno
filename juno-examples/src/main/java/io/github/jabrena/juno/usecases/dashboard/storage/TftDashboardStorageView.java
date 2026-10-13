package io.github.jabrena.juno.usecases.dashboard.storage;

import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardScreen;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.SdCapacity;
import io.github.jabrena.juno.usecases.dashboard.commons.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.TftHeaderWidget;

/**
 * Shows flash, RAM-arena and SD-card occupancy as three {@link TftOccupancyBar} rows. The SD row reads the shared
 * {@link TftDashboardState}; the view also provides the capacity service the SD thread calls.
 */
public final class TftDashboardStorageView implements TftDashboardScreen {
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_HEIGHT = 52;

    private static final int HOME_BUTTON_X = 196;
    private static final int HOME_BUTTON_Y = 200;
    private static final int HOME_BUTTON_WIDTH = 112;
    private static final int HOME_BUTTON_HEIGHT = 34;

    private final TftDashboardScreen home;
    private final TftDashboardState state;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("STORAGE");
    private final TftOccupancyBar flashBar = new TftOccupancyBar(FIRST_ROW_Y, "FLASH", TftTouchShield.MAGENTA, 1024,
            "KiB", "measuring...", "not available");
    private final TftOccupancyBar ramBar = new TftOccupancyBar(FIRST_ROW_Y + ROW_HEIGHT, "RAM ARENA",
            TftTouchShield.CYAN, 1, "B", "measuring...", "not available");
    private final TftOccupancyBar sdBar = new TftOccupancyBar(FIRST_ROW_Y + 2 * ROW_HEIGHT, "SD CARD",
            TftTouchShield.ORANGE, 1024, "MiB", "scanning card...", "no card");

    public TftDashboardStorageView(TftDashboardScreen home, TftDashboardState state, TftFpsWidget fps) {
        this.home = home;
        this.state = state;
        this.fps = fps;
    }

    /** The service behind the SD row; slow, since counting the free space can read the card's whole FAT. */
    public SdCapacity measureSdCapacity() {
        return new SdCapacity(SdCard.totalKiB(), SdCard.freeKiB());
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();
        drawHomeButton();
        fps.reset();

        flashBar.reset();
        ramBar.reset();
        sdBar.reset();
        flashBar.update(Memory.flashUsedBytes(), Memory.flashCapacityBytes());
    }

    @Override
    public void draw() {
        ramBar.update(Memory.arenaUsedBytes(), Memory.arenaCapacityBytes());
        SdCapacity capacity = state.sdCapacity();
        if (!state.cardReady() || capacity != null && !capacity.known()) {
            sdBar.showUnavailable();
        } else if (capacity == null) {
            sdBar.showPending();
        } else {
            sdBar.update(capacity.totalKiB() - capacity.freeKiB(), capacity.totalKiB());
        }
        fps.update();
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
}
