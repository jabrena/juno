package io.github.jabrena.juno.api.hardware.net.weather;

/** Parses the HTTP {@code Date} response header, which {@link WeatherTFT} sets its clock from. */
final class HttpDate {
    private HttpDate() {
    }

    /**
     * Returns the value of an HTTP {@code Date} header, such as {@code Sun, 27 Sep 2026 17:06:14 GMT}, as seconds
     * since the Unix epoch, or 0 when it is malformed.
     */
    static int parseDate(String value) {
        // Skip "Sun, " to reach "27 Sep 2026 17:06:14".
        int p = 0;
        while (p < value.length() && value.charAt(p) != ',') {
            p = p + 1;
        }
        p = p + 2;
        if (p + 20 > value.length()) {
            return 0;
        }
        int day = number(value, p, 2);
        int month = monthOf(lower(value.charAt(p + 3)), lower(value.charAt(p + 4)), lower(value.charAt(p + 5)));
        int year = number(value, p + 7, 4);
        int hour = number(value, p + 12, 2);
        int minute = number(value, p + 15, 2);
        int second = number(value, p + 18, 2);
        if (day < 1 || month < 1 || year < 2020 || hour < 0 || minute < 0 || second < 0) {
            return 0;
        }
        return CivilCalendar.daysFromCivil(year, month, day) * CivilCalendar.SECONDS_PER_DAY + hour * 3600 + minute * 60 + second;
    }

    private static int lower(int character) {
        if (character >= 'A' && character <= 'Z') {
            return character + 32;
        }
        return character;
    }

    private static int number(String text, int offset, int digits) {
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = text.charAt(offset + i) - '0';
            if (digit < 0 || digit > 9) {
                return -1;
            }
            value = value * 10 + digit;
        }
        return value;
    }

    private static int monthOf(int a, int b, int c) {
        if (a == 'j' && b == 'a') {
            return 1;
        }
        if (a == 'f') {
            return 2;
        }
        if (a == 'm' && c == 'r') {
            return 3;
        }
        if (a == 'a' && b == 'p') {
            return 4;
        }
        if (a == 'm') {
            return 5;
        }
        if (a == 'j' && c == 'n') {
            return 6;
        }
        if (a == 'j') {
            return 7;
        }
        if (a == 'a') {
            return 8;
        }
        if (a == 's') {
            return 9;
        }
        if (a == 'o') {
            return 10;
        }
        if (a == 'n') {
            return 11;
        }
        if (a == 'd') {
            return 12;
        }
        return 0;
    }
}
