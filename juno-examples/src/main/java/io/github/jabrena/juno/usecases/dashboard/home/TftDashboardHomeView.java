package io.github.jabrena.juno.usecases.dashboard.home;

import io.github.jabrena.juno.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.dashboard.commons.TftDashboardViews;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftButtonWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftFpsWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftHeaderWidget;
import io.github.jabrena.juno.usecases.dashboard.commons.ux.TftTextWidget;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** The home screen: a menu whose buttons ask the navigator for the status and storage views. */
public final class TftDashboardHomeView implements View {
    private final Navigator navigator;
    private final TftFpsWidget fps;
    private final TftHeaderWidget header = new TftHeaderWidget("JUNO DASHBOARD");
    private final TftTextWidget prompt = new TftTextWidget(12, 64, 2);
    private final TftButtonWidget statusButton =
            new TftButtonWidget(72, 92, 176, 42, "VIEW STATUS", TftTouchShield.BLUE);
    private final TftButtonWidget storageButton =
            new TftButtonWidget(72, 146, 176, 42, "VIEW STORAGE", TftTouchShield.BLUE);

    public TftDashboardHomeView(Navigator navigator, TftFpsWidget fps) {
        this.navigator = navigator;
        this.fps = fps;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(TftTouchShield.NAVY);
        header.draw();
        prompt.draw("Select a view", TftTouchShield.WHITE);
        statusButton.draw();
        storageButton.draw();
        fps.reset();
    }

    @Override
    public void render() {
        fps.update();
    }

    @Override
    public void touch(int x, int y) {
        if (statusButton.contains(x, y)) {
            navigator.show(TftDashboardViews.STATUS);
        } else if (storageButton.contains(x, y)) {
            navigator.show(TftDashboardViews.STORAGE);
        }
    }
}
