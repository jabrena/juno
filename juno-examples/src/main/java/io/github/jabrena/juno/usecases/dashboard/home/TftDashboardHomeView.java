package io.github.jabrena.juno.usecases.dashboard.home;

import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardScreen;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.usecases.dashboard.commons.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.status.TftDashboardStatusView;
import io.github.jabrena.juno.usecases.dashboard.storage.TftDashboardStorageView;

/**
 * The home screen and the dashboard's entry point: a menu that opens the status and storage views. Both views are
 * created once here, at startup, and return to this screen. Home also starts the SD card and two virtual threads,
 * one that uses the card and one that uses Wi-Fi; they call the views' services and publish the records into the
 * {@link TftDashboardState} both views read. Only the SD thread touches the card, so it is never shared.
 */
public final class TftDashboardHomeView implements TftDashboardScreen {
    private final TftDashboardState state = new TftDashboardState();
    private final TftFpsWidget fps = new TftFpsWidget();
    private final TftHeaderWidget header = new TftHeaderWidget("JUNO DASHBOARD");
    private final TftButtonWidget statusButton =
            new TftButtonWidget(72, 92, 176, 42, "VIEW STATUS", TftTouchShield.BLUE);
    private final TftButtonWidget storageButton =
            new TftButtonWidget(72, 146, 176, 42, "VIEW STORAGE", TftTouchShield.BLUE);
    private final TftDashboardStatusView statusView;
    private final TftDashboardStorageView storageView;

    public TftDashboardHomeView() {
        this.statusView = new TftDashboardStatusView(this, state, fps);
        this.storageView = new TftDashboardStorageView(this, state, fps);
    }

    /** Starts the SD card, then the SD and Wi-Fi threads, so the display never waits for either. */
    public void startBackgroundWork() {
        state.publishCardReady(SdCard.begin());
        statusView.writeStartupStatus();
        Thread.startVirtualThread(this::runSdCard);
        Thread.startVirtualThread(this::runWifi);
    }

    /** The only thread that uses the card: the quick reads first, so Wi-Fi can start, then the slow capacity count. */
    private void runSdCard() {
        state.publishStatusFile(statusView.readStatusFile());
        state.publishCredentials(statusView.loadCredentials());
        state.publishSdCapacity(storageView.measureSdCapacity());
    }

    /** Waits for the credentials from the card, then joins the network while the SD thread keeps counting. */
    private void runWifi() {
        try {
            state.publishWifiStatus(statusView.connectWifi(state.awaitCredentials()));
        } catch (InterruptedException interrupted) {
            Serial.println("Wi-Fi thread interrupted");
            state.publishWifiStatus(new WifiStatus(false));
        }
    }

    @Override
    public void show() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(12, 64);
        TftTouchShield.print("Select a view");
        statusButton.draw();
        storageButton.draw();
        fps.reset();
    }

    @Override
    public void draw() {
        fps.update();
    }

    @Override
    public TftDashboardScreen touch(int x, int y) {
        if (statusButton.contains(x, y)) {
            return statusView;
        }
        if (storageButton.contains(x, y)) {
            return storageView;
        }
        return this;
    }

}
