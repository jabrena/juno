package io.github.jabrena.juno.usecases.supabase.dashboard.gathering;

import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;
import io.github.jabrena.juno.usecases.supabase.dashboard.services.SupabaseTemperatureService;

/** Displays bounded Supabase page collection progress, opening the histogram only after a complete collection. */
public final class TemperatureGatheringView implements View {
    private static final int UNSHOWN = -2;

    private final TemperatureDashboardState model;
    private final SupabaseTemperatureService service;
    private final Navigator navigator;
    private final int nextViewId;
    private int shownStatus = UNSHOWN;
    private int shownPage = UNSHOWN;
    private int shownTotalPages = UNSHOWN;

    public TemperatureGatheringView(
            TemperatureDashboardState model, SupabaseTemperatureService service, Navigator navigator, int nextViewId) {
        this.model = model;
        this.service = service;
        this.navigator = navigator;
        this.nextViewId = nextViewId;
    }

    @Override
    public void enter() {
        shownStatus = UNSHOWN;
        shownPage = UNSHOWN;
        shownTotalPages = UNSHOWN;
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        service.begin();
    }

    @Override
    public void render() {
        int status = model.status();
        if (status == TemperatureDashboardState.COMPLETE) {
            navigator.show(nextViewId);
            return;
        }
        if (status == shownStatus && model.page() == shownPage && model.totalPages() == shownTotalPages) {
            return;
        }
        shownStatus = status;
        shownPage = model.page();
        shownTotalPages = model.totalPages();
        draw();
    }

    @Override
    public void touch(int x, int y) {
    }

    private void draw() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(18, 54);
        TftTouchShield.print("SUPABASE");
        TftTouchShield.setCursor(18, 82);
        TftTouchShield.print("GATHERING");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(18, 132);
        if (model.status() == TemperatureDashboardState.CONNECTING) {
            TftTouchShield.print("Connecting WiFi...");
            return;
        }
        if (model.status() == TemperatureDashboardState.WIFI_FAILED) {
            TftTouchShield.print("WiFi failed");
            return;
        }
        if (model.status() == TemperatureDashboardState.PARTIAL) {
            TftTouchShield.print("HTTP request failed");
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
            TftTouchShield.setCursor(18, 172);
            TftTouchShield.print("See serial monitor");
            return;
        }
        TftTouchShield.print("page ");
        TftTouchShield.print(model.page());
        TftTouchShield.print("/");
        if (model.totalPages() >= 0) {
            TftTouchShield.print(model.totalPages());
        } else {
            TftTouchShield.print("?");
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
        TftTouchShield.setCursor(18, 172);
        TftTouchShield.print("rows ");
        TftTouchShield.print(model.readings() + model.missing());
        if (model.totalRows() >= 0) {
            TftTouchShield.print("/");
            TftTouchShield.print(model.totalRows());
        }
    }
}
