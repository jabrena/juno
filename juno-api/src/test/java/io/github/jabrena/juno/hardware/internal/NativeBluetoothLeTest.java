package io.github.jabrena.juno.hardware.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The board's BLE radio")
class NativeBluetoothLeTest {
    @Test
    @DisplayName("Is one shared instance, returned on every call")
    void shouldReturnTheSameRadioOnEveryCall() {
        // When & Then
        assertThat(NativeBluetoothLe.bluetoothLe()).isSameAs(NativeBluetoothLe.bluetoothLe());
    }
}
