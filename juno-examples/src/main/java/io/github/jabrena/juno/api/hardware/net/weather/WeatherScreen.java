package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Everything {@link WeatherTFT} draws on the TFT shield except the seven-segment clock. */
final class WeatherScreen {
    private static final int CITY_GREEN = 0x9FEB;
    private static final int TEMPERATURE_BLUE = 0x3D7F;
    private static final int BAR_BACKGROUND = 0x39E7;
    private static final int CLOUD_GRAY = 0xBDF7;

    // Layout.
    private static final int MARGIN = 8;
    private static final int CITY_Y = 8;
    private static final int WIND_Y = 34;
    private static final int ICON_X = 280;
    private static final int ICON_Y = 32;
    private static final int CONDITION_X = 204;
    private static final int CONDITION_Y = 76;
    private static final int TEMPERATURE_Y = 104;
    private static final int DATE_Y = 156;
    private static final int TEMPERATURE_BAR_Y = 184;
    private static final int HUMIDITY_BAR_Y = 212;
    private static final int BAR_X = 34;
    private static final int BAR_WIDTH = 150;
    private static final int BAR_HEIGHT = 10;
    private static final int STATUS_Y = 148;
    private static final int FOOTER_Y = 230;

    private WeatherScreen() {
    }

    static void showStatus(TftTouchShield tft, String text) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(CONDITION_X, STATUS_Y);
        tft.print(text);
    }

    // Small gray footer: the public IP ipinfo.io saw and the coordinates sent to Open-Meteo.
    static void drawNetworkInfo(TftTouchShield tft, byte[] publicIp, byte[] location) {
        tft.setTextSize(1);
        tft.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, FOOTER_Y);
        tft.print("IP ");
        tft.print(publicIp, AsciiText.length(publicIp));
        tft.print("  ");
        tft.print(location, AsciiText.length(location));
    }

    static void drawCity(TftTouchShield tft, byte[] city, int length) {
        tft.setTextSize(2);
        tft.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, CITY_Y);
        tft.print(city, Math.min(length, 20));
    }

    static void drawWeather(TftTouchShield tft, int temperature, int humidity, int windSpeed, int condition, boolean isDay) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, WIND_Y);
        tft.print("Wind ");
        tft.print(windSpeed);
        tft.print(" km/h   ");

        drawIcon(tft, condition, isDay);

        tft.fillRect(CONDITION_X, CONDITION_Y - 4, 108, 22, TftTouchShield.WHITE);
        tft.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        tft.setCursor(CONDITION_X + 6, CONDITION_Y);
        tft.print(WeatherConditions.label(condition));

        tft.setTextSize(5);
        tft.setTextColor(TEMPERATURE_BLUE, TftTouchShield.BLACK);
        tft.setCursor(CONDITION_X, TEMPERATURE_Y);
        tft.print(temperature);
        tft.print(" ");
        int degreeX = tft.width() - 20;
        tft.drawCircle(degreeX, TEMPERATURE_Y + 5, 4, TEMPERATURE_BLUE);
        tft.setTextSize(2);
        tft.setCursor(degreeX + 6, TEMPERATURE_Y);
        tft.print("C");

        drawThermometer(tft, MARGIN + 8, TEMPERATURE_BAR_Y + 4);
        drawBar(tft, TEMPERATURE_BAR_Y, (temperature + 10) * BAR_WIDTH / 50, TftTouchShield.YELLOW);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(BAR_X + BAR_WIDTH + 10, TEMPERATURE_BAR_Y - 2);
        tft.print(temperature);
        tft.print("C  ");

        drawDrop(tft, MARGIN + 8, HUMIDITY_BAR_Y + 4);
        drawBar(tft, HUMIDITY_BAR_Y, humidity * BAR_WIDTH / 100, TftTouchShield.CYAN);
        tft.setCursor(BAR_X + BAR_WIDTH + 10, HUMIDITY_BAR_Y - 2);
        tft.print(humidity);
        tft.print("%  ");
    }

    private static void drawBar(TftTouchShield tft, int y, int filled, int color) {
        int width = Math.max(0, Math.min(filled, BAR_WIDTH));
        tft.fillRect(BAR_X, y, width, BAR_HEIGHT, color);
        tft.fillRect(BAR_X + width, y, BAR_WIDTH - width, BAR_HEIGHT, BAR_BACKGROUND);
    }

    private static void drawThermometer(TftTouchShield tft, int x, int y) {
        tft.fillRect(x - 2, y - 12, 5, 12, TftTouchShield.RED);
        tft.fillCircle(x, y + 2, 5, TftTouchShield.RED);
    }

    private static void drawDrop(TftTouchShield tft, int x, int y) {
        tft.fillCircle(x, y + 2, 6, TftTouchShield.CYAN);
        tft.fillRect(x - 1, y - 10, 3, 6, TftTouchShield.CYAN);
        tft.fillRect(x - 3, y - 5, 7, 3, TftTouchShield.CYAN);
    }

    private static void drawIcon(TftTouchShield tft, int condition, boolean isDay) {
        tft.fillRect(ICON_X - 32, ICON_Y - 30, 64, 60, TftTouchShield.BLACK);
        if (condition == WeatherConditions.CLEAR) {
            drawSunOrMoon(tft, ICON_X, ICON_Y, 16, isDay);
            return;
        }
        if (condition == WeatherConditions.PARTLY_CLOUDY) {
            drawSunOrMoon(tft, ICON_X + 8, ICON_Y - 8, 12, isDay);
        }
        int cloudColor = CLOUD_GRAY;
        if (condition == WeatherConditions.STORM || condition == WeatherConditions.RAIN) {
            cloudColor = TftTouchShield.GRAY;
        }
        drawCloud(tft, ICON_X, ICON_Y + 2, cloudColor);
        if (condition == WeatherConditions.FOG) {
            for (int i = 0; i < 3; i++) {
                tft.fillRect(ICON_X - 24, ICON_Y + 16 + i * 5, 48, 2, CLOUD_GRAY);
            }
        } else if (condition == WeatherConditions.DRIZZLE || condition == WeatherConditions.RAIN) {
            for (int i = 0; i < 4; i++) {
                tft.fillRect(ICON_X - 18 + i * 11, ICON_Y + 18, 3, 8, TEMPERATURE_BLUE);
            }
        } else if (condition == WeatherConditions.SNOW) {
            for (int i = 0; i < 4; i++) {
                tft.fillCircle(ICON_X - 18 + i * 12, ICON_Y + 22, 2, TftTouchShield.WHITE);
            }
        } else if (condition == WeatherConditions.STORM) {
            tft.fillRect(ICON_X, ICON_Y + 14, 4, 7, TftTouchShield.YELLOW);
            tft.fillRect(ICON_X - 4, ICON_Y + 20, 8, 3, TftTouchShield.YELLOW);
            tft.fillRect(ICON_X - 4, ICON_Y + 22, 4, 7, TftTouchShield.YELLOW);
        }
    }

    private static void drawSunOrMoon(TftTouchShield tft, int x, int y, int r, boolean isDay) {
        if (isDay) {
            tft.fillCircle(x, y, r, TftTouchShield.YELLOW);
            tft.fillRect(x - 1, y - r - 8, 3, 5, TftTouchShield.YELLOW);
            tft.fillRect(x - 1, y + r + 3, 3, 5, TftTouchShield.YELLOW);
            tft.fillRect(x - r - 8, y - 1, 5, 3, TftTouchShield.YELLOW);
            tft.fillRect(x + r + 3, y - 1, 5, 3, TftTouchShield.YELLOW);
            return;
        }
        tft.fillCircle(x, y, r, TftTouchShield.WHITE);
        tft.fillCircle(x + r / 2, y - r / 3, r - 2, TftTouchShield.BLACK);
        drawStar(tft, x + r, y + r / 2, TftTouchShield.WHITE);
        drawStar(tft, x + r / 2 + 8, y + r + 2, TftTouchShield.WHITE);
    }

    private static void drawStar(TftTouchShield tft, int x, int y, int color) {
        tft.drawHorizontalLine(x - 3, y, 7, color);
        tft.drawVerticalLine(x, y - 3, 7, color);
    }

    private static void drawCloud(TftTouchShield tft, int x, int y, int color) {
        tft.fillCircle(x - 12, y + 4, 9, color);
        tft.fillCircle(x + 2, y - 3, 12, color);
        tft.fillCircle(x + 15, y + 5, 8, color);
        tft.fillRect(x - 12, y + 5, 27, 9, color);
    }

    static void drawDate(TftTouchShield tft, int days) {
        CivilCalendar.civilFromDays(days);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, DATE_Y);
        tft.print(CivilCalendar.civilYear);
        tft.print("/");
        tft.print(CivilCalendar.civilMonth);
        tft.print("/");
        tft.print(CivilCalendar.civilDay);
        tft.print("  ");
        tft.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        tft.setCursor(MARGIN + 144, DATE_Y);
        tft.print(CivilCalendar.weekdayName(CivilCalendar.floorMod(days + 4, 7)));
    }
}
