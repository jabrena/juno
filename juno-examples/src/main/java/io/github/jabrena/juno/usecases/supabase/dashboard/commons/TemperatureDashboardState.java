package io.github.jabrena.juno.usecases.supabase.dashboard.commons;

/**
 * Shared dashboard model, written by the Supabase service and read by the TFT view. It holds both loading state and
 * the median-temperature histogram. Temperatures are stored as integer tenths of a degree, and all storage is
 * allocated once at startup.
 */
public final class TemperatureDashboardState {
    public static final int CONNECTING = 0;
    public static final int READING = 1;
    public static final int COMPLETE = 2;
    public static final int PARTIAL = 3;
    public static final int WIFI_FAILED = 4;

    /** Marks a missing or null sensor reading. */
    public static final int MISSING = -1_000_000;
    /** Number of temperature values in one source row. */
    public static final int SENSORS = 3;

    private static final int LOWEST_DEGREES = 10;
    private static final int BINS = 30;
    private static final int BUCKETS = BINS + 2;
    private static final int UNSET_MINIMUM = 1_000_000;
    private static final int UNSET_MAXIMUM = -1_000_000;

    private final int[] counts = new int[BUCKETS];
    private int status = CONNECTING;
    private int page;
    private int pageRows;
    private int totalRows = -1;
    private int totalPages = -1;
    private int readings;
    private int missing;
    private int minimum = UNSET_MINIMUM;
    private int maximum = UNSET_MAXIMUM;

    public int status() {
        return status;
    }

    public int page() {
        return page;
    }

    public int pageRows() {
        return pageRows;
    }

    /** The exact number of rows reported by Supabase, or {@code -1} until it is known. */
    public int totalRows() {
        return totalRows;
    }

    /** The number of bounded Supabase pages required, or {@code -1} until the count response arrives. */
    public int totalPages() {
        return totalPages;
    }

    public int readings() {
        return readings;
    }

    public int missing() {
        return missing;
    }

    public int minimumTenths() {
        return minimum;
    }

    public int maximumTenths() {
        return maximum;
    }

    public int lowestDegrees() {
        return LOWEST_DEGREES;
    }

    public int highestDegrees() {
        return LOWEST_DEGREES + BINS;
    }

    public int binCount() {
        return BINS;
    }

    public int bucketCount() {
        return BUCKETS;
    }

    public int countAt(int bucket) {
        return counts[bucket];
    }

    public int peakCount() {
        int peak = 0;
        int bucket = 0;
        while (bucket < BUCKETS) {
            if (counts[bucket] > peak) {
                peak = counts[bucket];
            }
            bucket = bucket + 1;
        }
        return peak;
    }

    /** Publishes that Wi-Fi connected and the service is ready to read the first page. */
    public void publishReading() {
        status = READING;
    }

    /** Publishes one successfully parsed page. */
    public void publishPage(int currentPage, int rows) {
        page = currentPage;
        pageRows = rows;
    }

    /** Publishes the exact row count and its corresponding number of bounded pages. */
    public void publishTotalRows(int rows, int pageSize) {
        totalRows = rows;
        totalPages = (rows + pageSize - 1) / pageSize;
    }

    public void publishComplete() {
        status = COMPLETE;
    }

    public void publishPartial() {
        status = PARTIAL;
    }

    public void publishWifiFailed() {
        status = WIFI_FAILED;
    }

    /** Adds the middle value of one row's three readings, or marks the row missing if any value is absent. */
    public void addMedian(int first, int second, int third) {
        if (first == MISSING || second == MISSING || third == MISSING) {
            missing = missing + 1;
            return;
        }
        if (first > second) {
            int swap = first;
            first = second;
            second = swap;
        }
        if (second > third) {
            int swap = second;
            second = third;
            third = swap;
        }
        if (first > second) {
            second = first;
        }
        add(second);
    }

    private void add(int tenths) {
        readings = readings + 1;
        if (tenths < minimum) {
            minimum = tenths;
        }
        if (tenths > maximum) {
            maximum = tenths;
        }
        int bucket = bucketOf(tenths);
        counts[bucket] = counts[bucket] + 1;
    }

    private static int bucketOf(int tenths) {
        int lowest = LOWEST_DEGREES * 10;
        if (tenths < lowest) {
            return 0;
        }
        int bin = (tenths - lowest) / 10;
        if (bin >= BINS) {
            return BUCKETS - 1;
        }
        return bin + 1;
    }
}
