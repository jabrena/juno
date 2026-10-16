package io.github.jabrena.juno.usecases.supabase;

/**
 * UTC calendar time from one startup reading plus the monotonic millisecond clock, so the program asks the internet
 * for the time once rather than every cycle. Elapsed time is added in integer arithmetic and carries across days,
 * months and leap years. The millisecond counter wraps about every 24.8 days; subtraction keeps the elapsed time
 * correct across a wrap, but the result drifts with the board's oscillator and is never corrected here. Nothing is
 * allocated: one instance is created at startup and {@link #update} only writes its fields.
 */
final class SensorClock {
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_DAY = 86_400;
    private static final int UTC_TIMESTAMP_LENGTH = 20;
    /** Days from 0000-03-01 to 1970-01-01, the epoch of the civil-date algorithm below. */
    private static final int DAYS_FROM_YEAR_ZERO_TO_UNIX_EPOCH = 719_468;
    private static final int DAYS_PER_ERA = 146_097;
    private static final int YEARS_PER_ERA = 400;

    private int originDay;
    private int originSecond;
    private int originMillis;

    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;

    /** Fixes the calendar time that {@code millisNow} corresponds to. */
    void start(int year, int month, int day, int hour, int minute, int second, int millisNow) {
        originDay = daysFromCivil(year, month, day);
        originSecond = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second;
        originMillis = millisNow;
        update(millisNow);
    }

    /** Advances the calendar time to {@code millisNow}, which must not precede the start by more than 24 days. */
    void update(int millisNow) {
        int elapsedSeconds = (millisNow - originMillis) / MILLIS_PER_SECOND;
        int total = originSecond + elapsedSeconds;
        int secondOfDay = total % SECONDS_PER_DAY;
        int days = originDay + total / SECONDS_PER_DAY;

        hour = secondOfDay / SECONDS_PER_HOUR;
        minute = secondOfDay / SECONDS_PER_MINUTE % 60;
        second = secondOfDay % SECONDS_PER_MINUTE;
        civilFromDays(days);
    }

    int year() {
        return year;
    }

    int month() {
        return month;
    }

    int day() {
        return day;
    }

    int hour() {
        return hour;
    }

    int minute() {
        return minute;
    }

    int second() {
        return second;
    }

    /** Writes the UTC instant one hour before this clock's current time as {@code YYYY-MM-DDTHH:MM:SSZ}. */
    int writeUtcMinusOneHour(byte[] target, int capacity) {
        if (capacity < UTC_TIMESTAMP_LENGTH) {
            return -1;
        }
        int seconds = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second - SECONDS_PER_HOUR;
        int days = daysFromCivil(year, month, day);
        if (seconds < 0) {
            seconds = seconds + SECONDS_PER_DAY;
            days = days - 1;
        }
        int shifted = days + DAYS_FROM_YEAR_ZERO_TO_UNIX_EPOCH;
        int era = (shifted >= 0 ? shifted : shifted - (DAYS_PER_ERA - 1)) / DAYS_PER_ERA;
        int dayOfEra = shifted - era * DAYS_PER_ERA;
        int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        int monthIndex = (5 * dayOfYear + 2) / 153;
        int resultDay = dayOfYear - (153 * monthIndex + 2) / 5 + 1;
        int resultMonth = monthIndex < 10 ? monthIndex + 3 : monthIndex - 9;
        int resultYear = yearOfEra + era * YEARS_PER_ERA + (resultMonth <= 2 ? 1 : 0);

        writeFourDigits(target, 0, resultYear);
        target[4] = '-';
        writeTwoDigits(target, 5, resultMonth);
        target[7] = '-';
        writeTwoDigits(target, 8, resultDay);
        target[10] = 'T';
        writeTwoDigits(target, 11, seconds / SECONDS_PER_HOUR);
        target[13] = ':';
        writeTwoDigits(target, 14, seconds / SECONDS_PER_MINUTE % SECONDS_PER_MINUTE);
        target[16] = ':';
        writeTwoDigits(target, 17, seconds % SECONDS_PER_MINUTE);
        target[19] = 'Z';
        return UTC_TIMESTAMP_LENGTH;
    }

    /** Days since 1970-01-01 (Howard Hinnant's civil-date algorithm, proleptic Gregorian calendar). */
    static int daysFromCivil(int year, int month, int day) {
        int shiftedYear = month <= 2 ? year - 1 : year;
        int era = (shiftedYear >= 0 ? shiftedYear : shiftedYear - 399) / YEARS_PER_ERA;
        int yearOfEra = shiftedYear - era * YEARS_PER_ERA;
        int monthIndex = month > 2 ? month - 3 : month + 9;
        int dayOfYear = (153 * monthIndex + 2) / 5 + day - 1;
        int dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * DAYS_PER_ERA + dayOfEra - DAYS_FROM_YEAR_ZERO_TO_UNIX_EPOCH;
    }

    private static void writeFourDigits(byte[] target, int offset, int value) {
        target[offset] = (byte) ('0' + value / 1000 % 10);
        target[offset + 1] = (byte) ('0' + value / 100 % 10);
        target[offset + 2] = (byte) ('0' + value / 10 % 10);
        target[offset + 3] = (byte) ('0' + value % 10);
    }

    private static void writeTwoDigits(byte[] target, int offset, int value) {
        target[offset] = (byte) ('0' + value / 10);
        target[offset + 1] = (byte) ('0' + value % 10);
    }

    private void civilFromDays(int days) {
        int shifted = days + DAYS_FROM_YEAR_ZERO_TO_UNIX_EPOCH;
        int era = (shifted >= 0 ? shifted : shifted - (DAYS_PER_ERA - 1)) / DAYS_PER_ERA;
        int dayOfEra = shifted - era * DAYS_PER_ERA;
        int yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365;
        int dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        int monthIndex = (5 * dayOfYear + 2) / 153;
        day = dayOfYear - (153 * monthIndex + 2) / 5 + 1;
        month = monthIndex < 10 ? monthIndex + 3 : monthIndex - 9;
        year = yearOfEra + era * YEARS_PER_ERA + (month <= 2 ? 1 : 0);
    }
}
