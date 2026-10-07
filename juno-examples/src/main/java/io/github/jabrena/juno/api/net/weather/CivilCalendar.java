package io.github.jabrena.juno.api.net.weather;

/** Proleptic Gregorian calendar arithmetic on days since the Unix epoch, for {@link WeatherTFT}. */
final class CivilCalendar {
    static final int SECONDS_PER_DAY = 86400;

    // Scratch results of civilFromDays.
    static int civilYear;
    static int civilMonth;
    static int civilDay;

    private CivilCalendar() {
    }

    // Howard Hinnant's days_from_civil: days since 1970-01-01 for a proleptic Gregorian date.
    static int daysFromCivil(int year, int month, int day) {
        int y = year;
        if (month <= 2) {
            y = y - 1;
        }
        int era = floorDiv(y, 400);
        int yearOfEra = y - era * 400;
        int shiftedMonth = month + 9;
        if (month > 2) {
            shiftedMonth = month - 3;
        }
        int dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1;
        int dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * 146097 + dayOfEra - 719468;
    }

    // The inverse of daysFromCivil, into civilYear/civilMonth/civilDay.
    static void civilFromDays(int days) {
        int z = days + 719468;
        int era = floorDiv(z, 146097);
        int dayOfEra = z - era * 146097;
        int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        int shiftedMonth = (5 * dayOfYear + 2) / 153;
        civilDay = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1;
        civilMonth = shiftedMonth + 3;
        if (shiftedMonth >= 10) {
            civilMonth = shiftedMonth - 9;
        }
        civilYear = yearOfEra + era * 400;
        if (civilMonth <= 2) {
            civilYear = civilYear + 1;
        }
    }

    static int floorDiv(int value, int divisor) {
        int quotient = value / divisor;
        if (value % divisor != 0 && value < 0) {
            quotient = quotient - 1;
        }
        return quotient;
    }

    static int floorMod(int value, int divisor) {
        return value - floorDiv(value, divisor) * divisor;
    }

    static String weekdayName(int weekday) {
        if (weekday == 0) {
            return "Sun";
        }
        if (weekday == 1) {
            return "Mon";
        }
        if (weekday == 2) {
            return "Tue";
        }
        if (weekday == 3) {
            return "Wed";
        }
        if (weekday == 4) {
            return "Thu";
        }
        if (weekday == 5) {
            return "Fri";
        }
        return "Sat";
    }
}
