package io.github.jabrena.juno.usecases.supabase.dashboard.gathering;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;
import io.github.jabrena.juno.usecases.supabase.dashboard.services.SupabaseTemperatureService;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Displays bounded Supabase page collection progress, opening the histogram only after a complete collection. */
public final class TemperatureGatheringView implements View {

    private final TftTouchShield tft;
    private static final int UNSHOWN = -2;

    private final TemperatureDashboardState model;
    private final SupabaseTemperatureService service;
    private final Navigator navigator;
    private final int nextViewId;
    private int shownStatus = UNSHOWN;
    private int shownPage = UNSHOWN;
    private int shownTotalPages = UNSHOWN;

    public TemperatureGatheringView(TftTouchShield tft, TemperatureDashboardState model, SupabaseTemperatureService service, Navigator navigator, int nextViewId) {
        this.tft = tft;
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
        tft.setRotation(TftTouchShield.LANDSCAPE);
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
        draw(tft);
    }

    @Override
    public void touch(int x, int y) {
    }

    private void draw(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        tft.setCursor(18, 54);
        tft.print("SUPABASE");
        tft.setCursor(18, 82);
        tft.print("GATHERING");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(18, 132);
        if (model.status() == TemperatureDashboardState.CONNECTING) {
            tft.print("Connecting WiFi...");
            return;
        }
        if (model.status() == TemperatureDashboardState.WIFI_FAILED) {
            tft.print("WiFi failed");
            return;
        }
        if (model.status() == TemperatureDashboardState.PARTIAL) {
            tft.print("HTTP request failed");
            tft.setTextSize(1);
            tft.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
            tft.setCursor(18, 172);
            tft.print("See serial monitor");
            return;
        }
        tft.print("page ");
        tft.print(model.page());
        tft.print("/");
        if (model.totalPages() >= 0) {
            tft.print(model.totalPages());
        } else {
            tft.print("?");
        }
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.YELLOW, TftTouchShield.BLACK);
        tft.setCursor(18, 172);
        tft.print("rows ");
        tft.print(model.readings() + model.missing());
        if (model.totalRows() >= 0) {
            tft.print("/");
            tft.print(model.totalRows());
        }
    }
}
