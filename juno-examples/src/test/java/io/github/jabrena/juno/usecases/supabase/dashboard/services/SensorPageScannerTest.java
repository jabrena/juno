package io.github.jabrena.juno.usecases.supabase.dashboard.services;

import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SensorPageScannerTest {

    @Test
    void scansEveryRowAndKeepsTheLastTimestampForTheNextPage() {
        byte[] page = bytes("[{\"ts\":\"2026-10-17T11:54:50\",\"temp1\":23.5,\"temp2\":null,\"temp3\":22.1},"
                + "{\"ts\":\"2026-10-17T11:55:50\",\"temp1\":-1.5,\"temp2\":24,\"temp3\":null}]");
        SensorPageScanner scanner = new SensorPageScanner();

        int rows = scanner.scan(page, page.length, new TemperatureDashboardState());

        assertThat(rows).isEqualTo(2);
        assertThat(text(scanner.lastId(), scanner.lastIdLength())).isEqualTo("2026-10-17T11:55:50");
    }

    @Test
    void readsAnEmptyPageAsZeroRows() {
        byte[] page = bytes("[]");
        SensorPageScanner scanner = new SensorPageScanner();

        assertThat(scanner.scan(page, page.length, new TemperatureDashboardState())).isZero();
        assertThat(scanner.lastIdLength()).isZero();
    }

    @Test
    void rejectsATruncatedPage() {
        byte[] page = bytes("[{\"ts\":\"2026-10-17T11:54:50\",\"temp1\":23.5,\"temp2\":null,\"temp3\":22.1},"
                + "{\"ts\":\"2026-10-17T11:55:50\",\"temp1\":2");
        SensorPageScanner scanner = new SensorPageScanner();

        assertThat(scanner.scan(page, page.length, new TemperatureDashboardState())).isNegative();
    }

    @Test
    void rejectsARowWithoutATimestamp() {
        byte[] page = bytes("[{\"temp1\":23.5,\"temp2\":null,\"temp3\":22.1}]");
        SensorPageScanner scanner = new SensorPageScanner();

        assertThat(scanner.scan(page, page.length, new TemperatureDashboardState())).isNegative();
    }

    @Test
    void rejectsACursorLongerThanTheBuffer() {
        byte[] page = bytes("[{\"ts\":\"2026-10-17T11:54:50-this-value-is-far-too-long\",\"temp1\":20.0,"
                + "\"temp2\":20.0,\"temp3\":20.0}]");
        SensorPageScanner scanner = new SensorPageScanner();

        assertThat(scanner.scan(page, page.length, new TemperatureDashboardState())).isNegative();
    }

    @Test
    void ignoresBytesBeyondTheLengthGivenByTheCaller() {
        byte[] page = bytes("[{\"ts\":\"2026-10-17T11:56:50\",\"temp1\":20.0,\"temp2\":20.0,\"temp3\":20.0}]garbage");
        SensorPageScanner scanner = new SensorPageScanner();

        assertThat(scanner.scan(page, page.length - "garbage".length(), new TemperatureDashboardState())).isEqualTo(1);
        assertThat(text(scanner.lastId(), scanner.lastIdLength())).isEqualTo("2026-10-17T11:56:50");
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static String text(byte[] buffer, int length) {
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }
}
