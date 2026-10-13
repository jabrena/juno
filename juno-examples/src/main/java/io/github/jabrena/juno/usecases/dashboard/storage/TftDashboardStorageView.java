package io.github.jabrena.juno.usecases.dashboard.storage;

import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.SdCapacity;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftOccupancyBar;

/** Shows flash, RAM-arena and SD-card occupancy as three {@link TftOccupancyBar} rows; the SD row reads the model. */
public final class TftDashboardStorageView implements View {
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_HEIGHT = 52;

    private final Navigator navigator;
    private final TftDashboardState model;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("STORAGE");
    private final TftButtonWidget homeButton = new TftButtonWidget(196, 200, 112, 34, "HOME", TftTouchShield.GRAY);
    private final TftOccupancyBar flashBar = new TftOccupancyBar(FIRST_ROW_Y, "FLASH", TftTouchShield.MAGENTA, 1024,
            "KiB", "measuring...", "not available");
    private final TftOccupancyBar ramBar = new TftOccupancyBar(FIRST_ROW_Y + ROW_HEIGHT, "RAM ARENA",
            TftTouchShield.CYAN, 1, "B", "measuring...", "not available");
    private final TftOccupancyBar sdBar = new TftOccupancyBar(FIRST_ROW_Y + 2 * ROW_HEIGHT, "SD CARD",
            TftTouchShield.ORANGE, 1024, "MiB", "scanning card...", "no card");

    public TftDashboardStorageView(Navigator navigator, TftDashboardState model, TftFpsWidget fps) {
        this.navigator = navigator;
        this.model = model;
        this.fps = fps;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();
        homeButton.draw();
        fps.reset();

        flashBar.reset();
        ramBar.reset();
        sdBar.reset();
        flashBar.update(Memory.flashUsedBytes(), Memory.flashCapacityBytes());
    }

    @Override
    public void render() {
        ramBar.update(Memory.arenaUsedBytes(), Memory.arenaCapacityBytes());
        SdCapacity capacity = model.sdCapacity();
        if (!model.cardReady() || capacity != null && !capacity.known()) {
            sdBar.showUnavailable();
        } else if (capacity == null) {
            sdBar.showPending();
        } else {
            sdBar.update(capacity.totalKiB() - capacity.freeKiB(), capacity.totalKiB());
        }
        fps.update();
    }

    @Override
    public void touch(int x, int y) {
        if (homeButton.contains(x, y)) {
            navigator.back();
        }
    }

}
