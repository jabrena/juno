package io.github.jabrena.juno.usecases.dashboard.status;

import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.StatusFile;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState.WifiStatus;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftTextWidget;

/** The network status screen: the Wi-Fi, SD-card and STATUS.TXT state from the model, each line redrawn on change. */
public final class TftDashboardStatusView implements View {
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

    public TftDashboardStatusView(Navigator navigator, TftDashboardState model, TftFpsWidget fps) {
        this.navigator = navigator;
        this.model = model;
        this.fps = fps;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();
        wifiLabel.draw("WIFI", TftTouchShield.WHITE);
        sdLabel.draw("SD", TftTouchShield.WHITE);
        fileLabel.draw("FILE", TftTouchShield.WHITE);
        homeButton.draw();
        fps.reset();
        wifiValue.reset();
        sdValue.reset();
        fileValue.reset();
    }

    @Override
    public void render() {
        WifiStatus wifi = model.wifiStatus();
        if (wifi == null) {
            wifiValue.set("STARTING", TftTouchShield.YELLOW);
        } else if (wifi.connected()) {
            wifiValue.set("OK", TftTouchShield.GREEN);
        } else {
            wifiValue.set("FAILED", TftTouchShield.RED);
        }
        StatusFile file = model.statusFile();
        if (!model.cardReady()) {
            sdValue.set("FAILED", TftTouchShield.RED);
        } else if (file == null) {
            sdValue.set("STARTING", TftTouchShield.YELLOW);
        } else if (file.readable()) {
            sdValue.set("OK", TftTouchShield.GREEN);
        } else {
            sdValue.set("FAILED", TftTouchShield.RED);
        }
        if (file == null || !file.readable()) {
            fileValue.set("-", TftTouchShield.WHITE);
        } else {
            fileValue.set(file.bytes(), " B", TftTouchShield.WHITE);
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
