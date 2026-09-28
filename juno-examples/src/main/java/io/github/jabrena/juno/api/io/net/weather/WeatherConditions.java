package io.github.jabrena.juno.api.io.net.weather;

/** {@link WeatherTFT}'s weather conditions, grouped from Open-Meteo's WMO weather codes. */
final class WeatherConditions {
    static final int CLEAR = 0;
    static final int PARTLY_CLOUDY = 1;
    static final int CLOUDY = 2;
    static final int FOG = 3;
    static final int DRIZZLE = 4;
    static final int RAIN = 5;
    static final int SNOW = 6;
    static final int STORM = 7;

    private WeatherConditions() {
    }

    static int of(int weatherCode) {
        if (weatherCode == 0) {
            return CLEAR;
        }
        if (weatherCode <= 2) {
            return PARTLY_CLOUDY;
        }
        if (weatherCode == 3) {
            return CLOUDY;
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return FOG;
        }
        if (weatherCode >= 51 && weatherCode <= 57) {
            return DRIZZLE;
        }
        if ((weatherCode >= 61 && weatherCode <= 67) || (weatherCode >= 80 && weatherCode <= 82)) {
            return RAIN;
        }
        if ((weatherCode >= 71 && weatherCode <= 77) || weatherCode == 85 || weatherCode == 86) {
            return SNOW;
        }
        if (weatherCode >= 95) {
            return STORM;
        }
        return CLOUDY;
    }

    static String label(int condition) {
        if (condition == CLEAR) {
            return "Clear   ";
        }
        if (condition == PARTLY_CLOUDY) {
            return "Partly  ";
        }
        if (condition == CLOUDY) {
            return "Cloudy  ";
        }
        if (condition == FOG) {
            return "Fog     ";
        }
        if (condition == DRIZZLE) {
            return "Drizzle ";
        }
        if (condition == RAIN) {
            return "Rain    ";
        }
        if (condition == SNOW) {
            return "Snow    ";
        }
        return "Storm   ";
    }
}
