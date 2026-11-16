package io.github.jabrena.juno.hardware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static io.github.jabrena.juno.hardware.security.NamedSlotStoreTest.ascii;
import static io.github.jabrena.juno.hardware.security.NamedSlotStoreTest.record;
import static org.assertj.core.api.Assertions.assertThat;

class Pico2WTrustZoneStoreTest {

    private FakeFirmware firmware;
    private Pico2WTrustZoneStore secrets;

    @BeforeEach
    void setUp() {
        firmware = new FakeFirmware(true);
        secrets = new Pico2WTrustZoneStore(firmware);
    }

    @Test
    @DisplayName("a written value is one firmware slot holding the name and the value, and reads back by name")
    void writeThenRead() {
        assertThat(secrets.isAvailable()).isTrue();
        assertThat(secrets.write("wifi.ssid", ascii("MyNetwork"), 9)).isEqualTo(9);

        assertThat(firmware.slots).containsOnlyKeys(0);
        assertThat(firmware.slots.get(0)).isEqualTo(record("wifi.ssid", "MyNetwork"));
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("clear is a zero-length write to the name's slot, and clearAll is the gateway's own call")
    void clearAndClearAll() {
        byte[] value = {1, 2, 3};
        secrets.write("wifi.ssid", value, 3);
        secrets.write("wifi.password", value, 3);

        assertThat(secrets.clear("wifi.ssid")).isZero();
        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.read("wifi.password", new byte[63])).isEqualTo(3);

        assertThat(secrets.clearAll()).isZero();
        assertThat(firmware.slots).isEmpty();
        assertThat(firmware.clearAllCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("a refused or short write from the firmware is reported as a failure")
    void firmwareRefusal() {
        secrets.write("wifi.ssid", new byte[] {1}, 1);
        firmware.refuseWrites = true;

        assertThat(secrets.write("wifi.ssid", new byte[] {2}, 1)).isEqualTo(-1);
        assertThat(secrets.clear("wifi.ssid")).isEqualTo(-1);
    }

    @Test
    @DisplayName("without the secure firmware every name reads missing and nothing is written")
    void withoutFirmware() {
        FakeFirmware absent = new FakeFirmware(false);
        Pico2WTrustZoneStore store = new Pico2WTrustZoneStore(absent);

        assertThat(store.isAvailable()).isFalse();
        assertThat(store.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(store.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(store.clearAll()).isEqualTo(-1);
        assertThat(absent.calls).isZero();
    }

    /** The gateway, as the secure firmware answers it. */
    private static final class FakeFirmware implements Pico2WTrustZoneStore.Transport {
        private final boolean present;
        private final Map<Integer, byte[]> slots = new HashMap<>();
        private boolean refuseWrites;
        private int calls;
        private int clearAllCalls;

        FakeFirmware(boolean present) {
            this.present = present;
        }

        @Override
        public boolean begin() {
            return present;
        }

        @Override
        public int read(int slot, byte[] buffer, int capacity) {
            calls++;
            byte[] value = slots.get(slot);
            if (value == null || value.length > capacity) {
                return -1;
            }
            System.arraycopy(value, 0, buffer, 0, value.length);
            return value.length;
        }

        @Override
        public int write(int slot, byte[] value, int length) {
            calls++;
            if (refuseWrites) {
                return -1;
            }
            if (length == 0) {
                slots.remove(slot);
            } else {
                slots.put(slot, Arrays.copyOf(value, length));
            }
            return length;
        }

        @Override
        public boolean clearAll() {
            calls++;
            clearAllCalls++;
            slots.clear();
            return true;
        }
    }
}
