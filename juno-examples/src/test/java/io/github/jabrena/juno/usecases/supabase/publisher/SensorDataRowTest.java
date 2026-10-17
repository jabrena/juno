package io.github.jabrena.juno.usecases.supabase.publisher;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SensorDataRowTest {

    @Test
    void writesTheSensorDataInsertPayload() {
        byte[] buffer = new byte[96];
        SensorDataRow row = new SensorDataRow(buffer, buffer.length);

        int length = row.write(23.5f, 24.1f, 22.8f, 2026, 10, 16, 12, 0, 0);

        assertThat(new String(buffer, 0, length, StandardCharsets.US_ASCII)).isEqualTo(
                "{\"temp1\":23.5,\"temp2\":24.1,\"temp3\":22.8,\"ts\":\"2026-10-16T12:00:00Z\"}");
    }

    @Test
    void roundsToOneDecimalWritesNegativesAndPadsTheTimestamp() {
        byte[] buffer = new byte[96];
        SensorDataRow row = new SensorDataRow(buffer, buffer.length);

        int length = row.write(-0.5f, -12.3f, 0.66f, 2026, 1, 2, 3, 4, 5);

        assertThat(new String(buffer, 0, length, StandardCharsets.US_ASCII)).isEqualTo(
                "{\"temp1\":-0.5,\"temp2\":-12.3,\"temp3\":0.7,\"ts\":\"2026-01-02T03:04:05Z\"}");
    }

    @Test
    void writesAMissingReadingAsNull() {
        byte[] buffer = new byte[96];
        SensorDataRow row = new SensorDataRow(buffer, buffer.length);

        int length = row.write(SensorDataRow.NO_READING, 24.0f, SensorDataRow.NO_READING, 2026, 10, 16, 12, 0, 0);

        assertThat(new String(buffer, 0, length, StandardCharsets.US_ASCII)).isEqualTo(
                "{\"temp1\":null,\"temp2\":24.0,\"temp3\":null,\"ts\":\"2026-10-16T12:00:00Z\"}");
    }

    @Test
    void rejectsARowThatDoesNotFitTheBuffer() {
        byte[] buffer = new byte[96];
        SensorDataRow row = new SensorDataRow(buffer, 32);

        assertThat(row.write(23.5f, 24.1f, 22.8f, 2026, 10, 16, 12, 0, 0)).isEqualTo(-1);
        assertThat(row.write(23.5f, 24.1f, 22.8f, 2026, 10, 16, 12, 0, 0)).isEqualTo(-1);
    }
}
