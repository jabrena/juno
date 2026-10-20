package io.github.jabrena.juno.usecases.supabase.dashboard.status;

import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;

/** The dashboard view: it observes the shared model and redraws only when its status or page changes. */
public final class TemperatureHistogramView implements View {
    private static final int UNSHOWN = -1;
    private static final int SERIAL_BAR_WIDTH = 50;
    private static final int TFT_PLOT_X = 34;
    private static final int TFT_BAR_STEP = 9;
    private static final int TFT_BAR_WIDTH = 7;
    private static final int TFT_BAR_HEIGHT = 120;
    private static final int TFT_PLOT_TOP = 64;
    private static final int TFT_BASELINE = 197;

    private final TemperatureDashboardState model;
    private int shownStatus = UNSHOWN;
    private int shownPage = UNSHOWN;

    public TemperatureHistogramView(TemperatureDashboardState model) {
        this.model = model;
    }

    @Override
    public void enter() {
        shownStatus = UNSHOWN;
        shownPage = UNSHOWN;
        render();
    }

    @Override
    public void render() {
        int status = model.status();
        int page = model.page();
        if (status == shownStatus && (status != TemperatureDashboardState.READING || page == shownPage)) {
            return;
        }
        shownStatus = status;
        shownPage = page;
        if (status == TemperatureDashboardState.CONNECTING) {
            showStatus("Connecting WiFi...");
        } else if (status == TemperatureDashboardState.READING) {
            showReadingProgress();
        } else if (status == TemperatureDashboardState.WIFI_FAILED) {
            showStatus("WiFi connection failed");
        } else {
            boolean complete = status == TemperatureDashboardState.COMPLETE;
            printHistogram();
            drawHistogram(complete);
            Serial.println("TFT histogram rendered");
            if (complete) {
                Serial.println("All rows read");
            } else {
                Serial.println("Read stopped early: the histogram above is partial");
            }
        }
    }

    /** This read-only dashboard has no touch action. */
    @Override
    public void touch(int x, int y) {
    }

    private void showReadingProgress() {
        showStatus("Reading Supabase...");
        if (model.page() > 0) {
            TftTouchShield.setTextSize(1);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
            TftTouchShield.setCursor(12, 124);
            TftTouchShield.print("page ");
            TftTouchShield.print(model.page());
            TftTouchShield.print("  rows ");
            TftTouchShield.print(model.pageRows());
        }
    }

    private static void showStatus(String message) {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(12, 92);
        TftTouchShield.print(message);
    }

    private void printHistogram() {
        Serial.print("Median C: readings=");
        Serial.print(model.readings());
        Serial.print(" missing=");
        Serial.println(model.missing());
        if (model.readings() == 0) {
            return;
        }
        Serial.print("  min=");
        printSerialTenths(model.minimumTenths());
        Serial.print(" max=");
        printSerialTenths(model.maximumTenths());
        Serial.println("");

        int peak = model.peakCount();
        if (peak <= 0) {
            return;
        }
        int bucket = 0;
        while (bucket < model.bucketCount()) {
            printLabel(bucket);
            int count = model.countAt(bucket);
            int bars = count * SERIAL_BAR_WIDTH / peak;
            if (count > 0 && bars == 0) {
                bars = 1;
            }
            int bar = 0;
            while (bar < bars) {
                Serial.print("#");
                bar = bar + 1;
            }
            Serial.print(" ");
            Serial.println(count);
            bucket = bucket + 1;
        }
    }

    private void drawHistogram(boolean complete) {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(6, 6);
        TftTouchShield.print("MEDIAN TEMPERATURE");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(complete ? TftTouchShield.GREEN : TftTouchShield.YELLOW,
                TftTouchShield.BLACK);
        TftTouchShield.setCursor(270, 10);
        TftTouchShield.print(complete ? "ALL" : "PARTIAL");
        drawSummary();
        drawChart();
    }

    private void drawSummary() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(6, 34);
        TftTouchShield.print("rows ");
        TftTouchShield.print(model.readings());
        TftTouchShield.print("  missing ");
        TftTouchShield.print(model.missing());
        if (model.readings() == 0) {
            TftTouchShield.setCursor(6, 46);
            TftTouchShield.print("No complete rows");
            return;
        }
        TftTouchShield.setCursor(174, 34);
        TftTouchShield.print("min ");
        printTftTenths(model.minimumTenths());
        TftTouchShield.setCursor(246, 34);
        TftTouchShield.print("max ");
        printTftTenths(model.maximumTenths());
    }

    private void drawChart() {
        int bins = model.binCount();
        drawYAxis(bins);
        TftTouchShield.drawHorizontalLine(TFT_PLOT_X, TFT_BASELINE, bins * TFT_BAR_STEP,
                TftTouchShield.GRAY);
        int grid = 0;
        while (grid <= bins) {
            TftTouchShield.drawVerticalLine(TFT_PLOT_X + grid * TFT_BAR_STEP, TFT_PLOT_TOP,
                    TFT_BASELINE - TFT_PLOT_TOP, TftTouchShield.NAVY);
            grid = grid + 10;
        }
        if (model.readings() > 0) {
            int peak = model.peakCount();
            if (peak > 0) {
                int bucket = 1;
                while (bucket <= bins) {
                    int count = model.countAt(bucket);
                    int height = count * TFT_BAR_HEIGHT / peak;
                    if (count > 0 && height == 0) {
                        height = 1;
                    }
                    if (height > 0) {
                        int x = TFT_PLOT_X + (bucket - 1) * TFT_BAR_STEP + 1;
                        TftTouchShield.fillRect(x, TFT_BASELINE - height, TFT_BAR_WIDTH, height,
                                TftTouchShield.CYAN);
                    }
                    bucket = bucket + 1;
                }
            }
        }
        drawAxisLabels();
        TftTouchShield.setCursor(6, 228);
        TftTouchShield.print("<10: ");
        TftTouchShield.print(model.countAt(0));
        TftTouchShield.setCursor(244, 228);
        TftTouchShield.print(">=40: ");
        TftTouchShield.print(model.countAt(model.bucketCount() - 1));
    }

    /** Draws the count legend and its horizontal guide lines before the bars. */
    private void drawYAxis(int bins) {
        int width = bins * TFT_BAR_STEP;
        int peak = model.peakCount();
        int midpoint = TFT_PLOT_TOP + TFT_BAR_HEIGHT / 2;
        TftTouchShield.drawHorizontalLine(TFT_PLOT_X, TFT_PLOT_TOP, width, TftTouchShield.NAVY);
        TftTouchShield.drawHorizontalLine(TFT_PLOT_X, midpoint, width, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(2, 50);
        TftTouchShield.print("count");
        TftTouchShield.setCursor(4, TFT_PLOT_TOP - 4);
        TftTouchShield.print(peak);
        TftTouchShield.setCursor(4, midpoint - 4);
        TftTouchShield.print(peak / 2);
        TftTouchShield.setCursor(22, TFT_BASELINE - 4);
        TftTouchShield.print("0");
    }

    private void drawAxisLabels() {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(TFT_PLOT_X - 4, 206);
        TftTouchShield.print(model.lowestDegrees());
        TftTouchShield.setCursor(TFT_PLOT_X + 10 * TFT_BAR_STEP - 4, 206);
        TftTouchShield.print(model.lowestDegrees() + 10);
        TftTouchShield.setCursor(TFT_PLOT_X + 20 * TFT_BAR_STEP - 4, 206);
        TftTouchShield.print(model.lowestDegrees() + 20);
        TftTouchShield.setCursor(TFT_PLOT_X + 30 * TFT_BAR_STEP - 8, 206);
        TftTouchShield.print(model.highestDegrees());
        TftTouchShield.print(" C");
    }

    private void printLabel(int bucket) {
        if (bucket == 0) {
            Serial.print("  < ");
            Serial.print(model.lowestDegrees());
            Serial.print("C |");
            return;
        }
        if (bucket == model.bucketCount() - 1) {
            Serial.print(" >= ");
            Serial.print(model.highestDegrees());
            Serial.print("C |");
            return;
        }
        Serial.print("  ");
        Serial.print(model.lowestDegrees() + bucket - 1);
        Serial.print("C |");
    }

    private static void printSerialTenths(int tenths) {
        int magnitude = tenths;
        if (tenths < 0) {
            Serial.print("-");
            magnitude = -tenths;
        }
        Serial.print(magnitude / 10);
        Serial.print(".");
        Serial.print(magnitude % 10);
    }

    private static void printTftTenths(int tenths) {
        int magnitude = tenths;
        if (tenths < 0) {
            TftTouchShield.print("-");
            magnitude = -tenths;
        }
        TftTouchShield.print(magnitude / 10);
        TftTouchShield.print(".");
        TftTouchShield.print(magnitude % 10);
    }
}
