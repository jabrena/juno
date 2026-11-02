package io.github.jabrena.juno.usecases.dashboard.status;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.StatusFile;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftTextWidget;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The network status screen: the Wi-Fi, SD-card and STATUS.TXT state from the model, each line redrawn on change. */
public final class TftDashboardStatusView implements View {

    private final TftTouchShield tft;
    private static final int LABEL_X = 12;
    private static final int VALUE_X = 90;
    private static final int WIFI_Y = 56;
    private static final int SD_Y = 92;
    private static final int FILE_Y = 128;

    private final Navigator navigator;
    private final TftDashboardState model;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("NETWORK STATUS");
    private final TftButtonWidget homeButton = new TftButtonWidget(196, 200, 112, 34, "HOME", TftTouchShield.GRAY);
    private final TftTextWidget wifiLabel = new TftTextWidget(LABEL_X, WIFI_Y, 2);
    private final TftTextWidget sdLabel = new TftTextWidget(LABEL_X, SD_Y, 2);
    private final TftTextWidget fileLabel = new TftTextWidget(LABEL_X, FILE_Y, 2);
    private final TftTextWidget wifiValue = new TftTextWidget(VALUE_X, WIFI_Y, 2);
    private final TftTextWidget sdValue = new TftTextWidget(VALUE_X, SD_Y, 2);
    private final TftTextWidget fileValue = new TftTextWidget(VALUE_X, FILE_Y, 2);

    public TftDashboardStatusView(TftTouchShield tft, Navigator navigator, TftDashboardState model, TftFpsWidget fps) {
        this.tft = tft;
        this.navigator = navigator;
        this.model = model;
        this.fps = fps;
    }

    @Override
    public void enter() {
        tft.fillScreen(TftTouchShield.NAVY);
        header.draw(tft);
        wifiLabel.draw(tft, "WIFI", TftTouchShield.WHITE);
        sdLabel.draw(tft, "SD", TftTouchShield.WHITE);
        fileLabel.draw(tft, "FILE", TftTouchShield.WHITE);
        homeButton.draw(tft);
        fps.reset();
        wifiValue.reset();
        sdValue.reset();
        fileValue.reset();
    }

    @Override
    public void render() {
        WifiStatus wifi = model.wifiStatus();
        if (wifi == null) {
            wifiValue.set(tft, "STARTING", TftTouchShield.YELLOW);
        } else if (wifi.connected()) {
            wifiValue.set(tft, "OK", TftTouchShield.GREEN);
        } else {
            wifiValue.set(tft, "FAILED", TftTouchShield.RED);
        }
        StatusFile file = model.statusFile();
        if (!model.cardReady()) {
            sdValue.set(tft, "FAILED", TftTouchShield.RED);
        } else if (file == null) {
            sdValue.set(tft, "STARTING", TftTouchShield.YELLOW);
        } else if (file.readable()) {
            sdValue.set(tft, "OK", TftTouchShield.GREEN);
        } else {
            sdValue.set(tft, "FAILED", TftTouchShield.RED);
        }
        if (file == null || !file.readable()) {
            fileValue.set(tft, "-", TftTouchShield.WHITE);
        } else {
            fileValue.set(tft, file.bytes(), " B", TftTouchShield.WHITE);
        }
        fps.update(tft);
    }

    @Override
    public void touch(int x, int y) {
        if (homeButton.contains(x, y)) {
            navigator.back();
        }
    }
}
