package io.github.jabrena.juno.api.net.weather;

/** Parses the HTTP {@code Date} response header, which {@link WeatherTFT} sets its clock from. */
final class HttpDate {
    private HttpDate() {
    }

    /**
     * Finds the {@code Date: Sun, 27 Sep 2026 17:06:14 GMT} response header and returns it as
     * seconds since the Unix epoch, or 0 when it is missing or malformed.
     */
    static int parseDateHeader(byte[] headers, int length) {
        int start = -1;
        for (int i = 0; i + 5 < length && start < 0; i++) {
            if ((i == 0 || headers[i - 1] == '\n')
                    && lower(headers[i]) == 'd' && lower(headers[i + 1]) == 'a'
                    && lower(headers[i + 2]) == 't' && lower(headers[i + 3]) == 'e'
                    && headers[i + 4] == ':') {
                start = i + 5;
            }
        }
        if (start < 0) {
            return 0;
        }
        // Skip the space and "Sun, " to reach "27 Sep 2026 17:06:14".
        int p = start;
        while (p < length && headers[p] != ',') {
            p = p + 1;
        }
        p = p + 2;
        if (p + 20 > length) {
            return 0;
        }
        int day = number(headers, p, 2);
        int month = monthOf(lower(headers[p + 3]), lower(headers[p + 4]), lower(headers[p + 5]));
        int year = number(headers, p + 7, 4);
        int hour = number(headers, p + 12, 2);
        int minute = number(headers, p + 15, 2);
        int second = number(headers, p + 18, 2);
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

    private static int number(byte[] text, int offset, int digits) {
        int value = 0;
        for (int i = 0; i < digits; i++) {
            int digit = text[offset + i] - '0';
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
