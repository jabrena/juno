package io.github.jabrena.juno.usecases.supabase.publisher;

/**
 * Reusable sensor reading passed from acquisition to persistence. The same instance is updated for every cycle so a
 * board loop does not allocate a record on each iteration.
 */
final class GroveTemperatureRecord {
    private float temp1;
    private float temp2;
    private float temp3;
    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;

    void set(float temp1, float temp2, float temp3,
            int year, int month, int day, int hour, int minute, int second) {
        this.temp1 = temp1;
        this.temp2 = temp2;
        this.temp3 = temp3;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    float temp1() {
        return temp1;
    }

    float temp2() {
        return temp2;
    }

    float temp3() {
        return temp3;
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
}
