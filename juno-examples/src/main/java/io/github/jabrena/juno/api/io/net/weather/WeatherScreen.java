package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.tft.TftTouchShield;

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

    static void showStatus(String text) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CONDITION_X, STATUS_Y);
        TftTouchShield.print(text);
    }

    // Small gray footer: the public IP ipinfo.io saw and the coordinates sent to Open-Meteo.
    static void drawNetworkInfo(byte[] publicIp, byte[] location) {
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.GRAY, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, FOOTER_Y);
        TftTouchShield.print("IP ");
        TftTouchShield.print(publicIp, AsciiText.length(publicIp));
        TftTouchShield.print("  ");
        TftTouchShield.print(location, AsciiText.length(location));
    }

    static void drawCity(byte[] city, int length) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, CITY_Y);
        TftTouchShield.print(city, Math.min(length, 20));
    }

    static void drawWeather(int temperature, int humidity, int windSpeed, int condition, boolean isDay) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, WIND_Y);
        TftTouchShield.print("Wind ");
        TftTouchShield.print(windSpeed);
        TftTouchShield.print(" km/h   ");

        drawIcon(condition, isDay);

        TftTouchShield.fillRect(CONDITION_X, CONDITION_Y - 4, 108, 22, TftTouchShield.WHITE);
        TftTouchShield.setTextColor(TftTouchShield.BLACK, TftTouchShield.WHITE);
        TftTouchShield.setCursor(CONDITION_X + 6, CONDITION_Y);
        TftTouchShield.print(WeatherConditions.label(condition));

        TftTouchShield.setTextSize(5);
        TftTouchShield.setTextColor(TEMPERATURE_BLUE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(CONDITION_X, TEMPERATURE_Y);
        TftTouchShield.print(temperature);
        TftTouchShield.print(" ");
        int degreeX = TftTouchShield.width() - 20;
        TftTouchShield.drawCircle(degreeX, TEMPERATURE_Y + 5, 4, TEMPERATURE_BLUE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setCursor(degreeX + 6, TEMPERATURE_Y);
        TftTouchShield.print("C");

        drawThermometer(MARGIN + 8, TEMPERATURE_BAR_Y + 4);
        drawBar(TEMPERATURE_BAR_Y, (temperature + 10) * BAR_WIDTH / 50, TftTouchShield.YELLOW);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(BAR_X + BAR_WIDTH + 10, TEMPERATURE_BAR_Y - 2);
        TftTouchShield.print(temperature);
        TftTouchShield.print("C  ");

        drawDrop(MARGIN + 8, HUMIDITY_BAR_Y + 4);
        drawBar(HUMIDITY_BAR_Y, humidity * BAR_WIDTH / 100, TftTouchShield.CYAN);
        TftTouchShield.setCursor(BAR_X + BAR_WIDTH + 10, HUMIDITY_BAR_Y - 2);
        TftTouchShield.print(humidity);
        TftTouchShield.print("%  ");
    }

    private static void drawBar(int y, int filled, int color) {
        int width = Math.max(0, Math.min(filled, BAR_WIDTH));
        TftTouchShield.fillRect(BAR_X, y, width, BAR_HEIGHT, color);
        TftTouchShield.fillRect(BAR_X + width, y, BAR_WIDTH - width, BAR_HEIGHT, BAR_BACKGROUND);
    }

    private static void drawThermometer(int x, int y) {
        TftTouchShield.fillRect(x - 2, y - 12, 5, 12, TftTouchShield.RED);
        TftTouchShield.fillCircle(x, y + 2, 5, TftTouchShield.RED);
    }

    private static void drawDrop(int x, int y) {
        TftTouchShield.fillCircle(x, y + 2, 6, TftTouchShield.CYAN);
        TftTouchShield.fillRect(x - 1, y - 10, 3, 6, TftTouchShield.CYAN);
        TftTouchShield.fillRect(x - 3, y - 5, 7, 3, TftTouchShield.CYAN);
    }

    private static void drawIcon(int condition, boolean isDay) {
        TftTouchShield.fillRect(ICON_X - 32, ICON_Y - 30, 64, 60, TftTouchShield.BLACK);
        if (condition == WeatherConditions.CLEAR) {
            drawSunOrMoon(ICON_X, ICON_Y, 16, isDay);
            return;
        }
        if (condition == WeatherConditions.PARTLY_CLOUDY) {
            drawSunOrMoon(ICON_X + 8, ICON_Y - 8, 12, isDay);
        }
        int cloudColor = CLOUD_GRAY;
        if (condition == WeatherConditions.STORM || condition == WeatherConditions.RAIN) {
            cloudColor = TftTouchShield.GRAY;
        }
        drawCloud(ICON_X, ICON_Y + 2, cloudColor);
        if (condition == WeatherConditions.FOG) {
            for (int i = 0; i < 3; i++) {
                TftTouchShield.fillRect(ICON_X - 24, ICON_Y + 16 + i * 5, 48, 2, CLOUD_GRAY);
            }
        } else if (condition == WeatherConditions.DRIZZLE || condition == WeatherConditions.RAIN) {
            for (int i = 0; i < 4; i++) {
                TftTouchShield.fillRect(ICON_X - 18 + i * 11, ICON_Y + 18, 3, 8, TEMPERATURE_BLUE);
            }
        } else if (condition == WeatherConditions.SNOW) {
            for (int i = 0; i < 4; i++) {
                TftTouchShield.fillCircle(ICON_X - 18 + i * 12, ICON_Y + 22, 2, TftTouchShield.WHITE);
            }
        } else if (condition == WeatherConditions.STORM) {
            TftTouchShield.fillRect(ICON_X, ICON_Y + 14, 4, 7, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(ICON_X - 4, ICON_Y + 20, 8, 3, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(ICON_X - 4, ICON_Y + 22, 4, 7, TftTouchShield.YELLOW);
        }
    }

    private static void drawSunOrMoon(int x, int y, int r, boolean isDay) {
        if (isDay) {
            TftTouchShield.fillCircle(x, y, r, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - 1, y - r - 8, 3, 5, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - 1, y + r + 3, 3, 5, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x - r - 8, y - 1, 5, 3, TftTouchShield.YELLOW);
            TftTouchShield.fillRect(x + r + 3, y - 1, 5, 3, TftTouchShield.YELLOW);
            return;
        }
        TftTouchShield.fillCircle(x, y, r, TftTouchShield.WHITE);
        TftTouchShield.fillCircle(x + r / 2, y - r / 3, r - 2, TftTouchShield.BLACK);
        drawStar(x + r, y + r / 2, TftTouchShield.WHITE);
        drawStar(x + r / 2 + 8, y + r + 2, TftTouchShield.WHITE);
    }

    private static void drawStar(int x, int y, int color) {
        TftTouchShield.drawHorizontalLine(x - 3, y, 7, color);
        TftTouchShield.drawVerticalLine(x, y - 3, 7, color);
    }

    private static void drawCloud(int x, int y, int color) {
        TftTouchShield.fillCircle(x - 12, y + 4, 9, color);
        TftTouchShield.fillCircle(x + 2, y - 3, 12, color);
        TftTouchShield.fillCircle(x + 15, y + 5, 8, color);
        TftTouchShield.fillRect(x - 12, y + 5, 27, 9, color);
    }

    static void drawDate(int days) {
        CivilCalendar.civilFromDays(days);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, DATE_Y);
        TftTouchShield.print(CivilCalendar.civilYear);
        TftTouchShield.print("/");
        TftTouchShield.print(CivilCalendar.civilMonth);
        TftTouchShield.print("/");
        TftTouchShield.print(CivilCalendar.civilDay);
        TftTouchShield.print("  ");
        TftTouchShield.setTextColor(CITY_GREEN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN + 144, DATE_Y);
        TftTouchShield.print(CivilCalendar.weekdayName(CivilCalendar.floorMod(days + 4, 7)));
    }
}
