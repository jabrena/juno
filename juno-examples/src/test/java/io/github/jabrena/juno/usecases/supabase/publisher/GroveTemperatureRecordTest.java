package io.github.jabrena.juno.usecases.supabase.publisher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroveTemperatureRecordTest {

    @Test
    void reusesTheRecordStorageWhenAReadingIsUpdated() {
        GroveTemperatureRecord record = new GroveTemperatureRecord();

        record.set(23.5f, 24.0f, 22.8f, 2026, 10, 16, 12, 0, 0);
        record.set(23.6f, 24.1f, 22.9f, 2026, 10, 16, 12, 1, 0);

        assertThat(record.temp1()).isEqualTo(23.6f);
        assertThat(record.temp2()).isEqualTo(24.1f);
        assertThat(record.temp3()).isEqualTo(22.9f);
        assertThat(record.year()).isEqualTo(2026);
        assertThat(record.minute()).isEqualTo(1);
        assertThat(record.second()).isZero();
    }
}
