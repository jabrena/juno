package io.github.jabrena.juno.usecases.dashboard;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardState;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardViews;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.home.TftDashboardHomeView;
import io.github.jabrena.juno.usecases.dashboard.services.SdCardService;
import io.github.jabrena.juno.usecases.dashboard.services.WifiService;
import io.github.jabrena.juno.usecases.dashboard.status.TftDashboardStatusView;
import io.github.jabrena.juno.usecases.dashboard.storage.TftDashboardStorageView;
import io.github.jabrena.juno.util.mvc.ViewRouter;

/**
 * A touch dashboard that stays responsive while slow SD-card and Wi-Fi work runs in the background, built as
 * model-view-controller.
 *
 * <ul>
 *   <li><b>Model</b>: one {@link TftDashboardState}, written by the services and read by the views.</li>
 *   <li><b>Views</b>: home, network status and storage. Each renders the model, redrawing only what changed, and
 *       turns touches into requests to its navigator; no view holds a reference to another.</li>
 *   <li><b>Controller</b>: a {@link ViewRouter} owns the views, runs the frame loop on the main thread, and switches
 *       the active view when one asks to show another or to go back.</li>
 *   <li><b>Services</b>: {@link SdCardService} and {@link WifiService}, each on a virtual thread of its own,
 *       publish what they find into the model. Only the SD-card service touches the card.</li>
 * </ul>
 *
 * <p>This class only wires them together. Adding a view means one new class, one id in {@link TftDashboardViews}
 * and one {@code register} line here. Everything is allocated once at startup; the frame loop allocates nothing, and
 * prints its frame rate over serial once a second.
 *
 * <p>Put {@code application.properties}, containing {@code wifi.ssid} and {@code wifi.password}, in the SD-card
 * root. At startup the example creates or appends to {@code STATUS.TXT}.
 */
@Board(ArduinoUnoQ.class)
public final class DashboardTFT {
    private static final int FRAME_PERIOD_MS = 50;

    private DashboardTFT() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);

        TftDashboardState model = new TftDashboardState();
        TftFpsWidget fps = new TftFpsWidget();
        ViewRouter router = new ViewRouter(FRAME_PERIOD_MS);
        router.register(TftDashboardViews.HOME, new TftDashboardHomeView(router, fps));
        router.register(TftDashboardViews.STATUS, new TftDashboardStatusView(router, model, fps));
        router.register(TftDashboardViews.STORAGE, new TftDashboardStorageView(router, model, fps));
        // Draw before the SD start-up, so the screen is never blank while the card starts.
        router.start(TftDashboardViews.HOME);

        SdCardService sdCard = new SdCardService(model);
        sdCard.begin();
        sdCard.start();
        new WifiService(model).start();
        router.run();
    }
}
