package io.github.jabrena.juno.usecases.supabase.dashboard.status;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;
import io.github.jabrena.juno.util.mvc.View;

/** The dashboard view: it observes the shared model and redraws only when its status or page changes. */
public final class TemperatureHistogramView implements View {

    private final TftTouchShield tft;
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

    public TemperatureHistogramView(TftTouchShield tft, TemperatureDashboardState model) {
        this.tft = tft;
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
            showStatus(tft, "Connecting WiFi...");
        } else if (status == TemperatureDashboardState.READING) {
            showReadingProgress(tft);
        } else if (status == TemperatureDashboardState.WIFI_FAILED) {
            showStatus(tft, "WiFi connection failed");
        } else {
            boolean complete = status == TemperatureDashboardState.COMPLETE;
            printHistogram();
            drawHistogram(tft, complete);
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

    private void showReadingProgress(TftTouchShield tft) {
        showStatus(tft, "Reading Supabase...");
        if (model.page() > 0) {
            tft.setTextSize(1);
            tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
            tft.setCursor(12, 124);
            tft.print("page ");
            tft.print(model.page());
            tft.print("  rows ");
            tft.print(model.pageRows());
        }
    }

    private static void showStatus(TftTouchShield tft, String message) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        tft.setCursor(12, 92);
        tft.print(message);
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

    private void drawHistogram(TftTouchShield tft, boolean complete) {
        tft.fillScreen(TftTouchShield.BLACK);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(6, 6);
        tft.print("MEDIAN TEMPERATURE");
        tft.setTextSize(1);
        tft.setTextColor(complete ? TftTouchShield.GREEN : TftTouchShield.YELLOW,
                TftTouchShield.BLACK);
        tft.setCursor(270, 10);
        tft.print(complete ? "ALL" : "PARTIAL");
        drawSummary(tft);
        drawChart(tft);
    }

    private void drawSummary(TftTouchShield tft) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        tft.setCursor(6, 34);
        tft.print("rows ");
        tft.print(model.readings());
        tft.print("  missing ");
        tft.print(model.missing());
        if (model.readings() == 0) {
            tft.setCursor(6, 46);
            tft.print("No complete rows");
            return;
        }
        tft.setCursor(174, 34);
        tft.print("min ");
        printTftTenths(tft, model.minimumTenths());
        tft.setCursor(246, 34);
        tft.print("max ");
        printTftTenths(tft, model.maximumTenths());
    }

    private void drawChart(TftTouchShield tft) {
        int bins = model.binCount();
        drawYAxis(tft, bins);
        tft.drawHorizontalLine(TFT_PLOT_X, TFT_BASELINE, bins * TFT_BAR_STEP,
                TftTouchShield.GRAY);
        int grid = 0;
        while (grid <= bins) {
            tft.drawVerticalLine(TFT_PLOT_X + grid * TFT_BAR_STEP, TFT_PLOT_TOP,
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
                        tft.fillRect(x, TFT_BASELINE - height, TFT_BAR_WIDTH, height,
                                TftTouchShield.CYAN);
                    }
                    bucket = bucket + 1;
                }
            }
        }
        drawAxisLabels(tft);
        tft.setCursor(6, 228);
        tft.print("<10: ");
        tft.print(model.countAt(0));
        tft.setCursor(244, 228);
        tft.print(">=40: ");
        tft.print(model.countAt(model.bucketCount() - 1));
    }

    /** Draws the count legend and its horizontal guide lines before the bars. */
    private void drawYAxis(TftTouchShield tft, int bins) {
        int width = bins * TFT_BAR_STEP;
        int peak = model.peakCount();
        int midpoint = TFT_PLOT_TOP + TFT_BAR_HEIGHT / 2;
        tft.drawHorizontalLine(TFT_PLOT_X, TFT_PLOT_TOP, width, TftTouchShield.NAVY);
        tft.drawHorizontalLine(TFT_PLOT_X, midpoint, width, TftTouchShield.NAVY);
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(2, 50);
        tft.print("count");
        tft.setCursor(4, TFT_PLOT_TOP - 4);
        tft.print(peak);
        tft.setCursor(4, midpoint - 4);
        tft.print(peak / 2);
        tft.setCursor(22, TFT_BASELINE - 4);
        tft.print("0");
    }

    private void drawAxisLabels(TftTouchShield tft) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(TFT_PLOT_X - 4, 206);
        tft.print(model.lowestDegrees());
        tft.setCursor(TFT_PLOT_X + 10 * TFT_BAR_STEP - 4, 206);
        tft.print(model.lowestDegrees() + 10);
        tft.setCursor(TFT_PLOT_X + 20 * TFT_BAR_STEP - 4, 206);
        tft.print(model.lowestDegrees() + 20);
        tft.setCursor(TFT_PLOT_X + 30 * TFT_BAR_STEP - 8, 206);
        tft.print(model.highestDegrees());
        tft.print(" C");
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

    private static void printTftTenths(TftTouchShield tft, int tenths) {
        int magnitude = tenths;
        if (tenths < 0) {
            tft.print("-");
            magnitude = -tenths;
        }
        tft.print(magnitude / 10);
        tft.print(".");
        tft.print(magnitude % 10);
    }
}
