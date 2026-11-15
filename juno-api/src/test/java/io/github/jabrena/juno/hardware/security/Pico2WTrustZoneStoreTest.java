package io.github.jabrena.juno.hardware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Pico2WTrustZoneStoreTest {

    private static final int SSID_SLOT = 8;
    private static final SecretSlots TABLE = new SecretSlots().add("wifi.ssid", SSID_SLOT)
            .add("wifi.password", 9);

    private FakeFirmware firmware;
    private Pico2WTrustZoneStore secrets;

    @BeforeEach
    void setUp() {
        firmware = new FakeFirmware(true);
        secrets = new Pico2WTrustZoneStore(TABLE, firmware);
    }

    @Test
    @DisplayName("a written value reads back from its own slot at its own length")
    void writeThenRead() {
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[63];

        assertThat(secrets.isAvailable()).isTrue();
        assertThat(secrets.write("wifi.ssid", value, value.length)).isEqualTo(value.length);

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(value.length);
        assertThat(new String(buffer, 0, value.length, StandardCharsets.US_ASCII)).isEqualTo("MyNetwork");
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
        assertThat(firmware.slots).containsOnlyKeys(SSID_SLOT);
    }

    @Test
    @DisplayName("clear empties one name and clearAll empties every slot")
    void clearAndClearAll() {
        byte[] value = {1, 2, 3};
        secrets.write("wifi.ssid", value, 3);
        secrets.write("wifi.password", value, 3);

        assertThat(secrets.clear("wifi.ssid")).isZero();
        assertThat(secrets.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.read("wifi.password", new byte[63])).isEqualTo(3);

        assertThat(secrets.clearAll()).isZero();
        assertThat(firmware.slots).isEmpty();
    }

    @Test
    @DisplayName("an unknown name, a bad length or a value longer than the buffer is refused")
    void refusals() {
        byte[] value = new byte[64];
        secrets.write("wifi.ssid", value, 10);

        assertThat(secrets.write("wifi.unknown", value, 1)).isEqualTo(-1);
        assertThat(secrets.read("wifi.unknown", new byte[63])).isEqualTo(-1);
        assertThat(secrets.write("wifi.ssid", value, 64)).isEqualTo(-1);
        assertThat(secrets.write("wifi.ssid", value, -1)).isEqualTo(-1);
        assertThat(secrets.write("wifi.ssid", new byte[2], 3)).isEqualTo(-1);
        assertThat(secrets.read("wifi.ssid", new byte[9])).isEqualTo(-1);
    }

    @Test
    @DisplayName("a refused or short write from the firmware is reported as a failure")
    void firmwareRefusal() {
        firmware.refuseWrites = true;

        assertThat(secrets.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(secrets.clear("wifi.ssid")).isEqualTo(-1);
    }

    @Test
    @DisplayName("without the secure firmware every name reads missing and nothing is written")
    void withoutFirmware() {
        FakeFirmware absent = new FakeFirmware(false);
        Pico2WTrustZoneStore store = new Pico2WTrustZoneStore(TABLE, absent);

        assertThat(store.isAvailable()).isFalse();
        assertThat(store.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(store.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(store.clearAll()).isEqualTo(-1);
        assertThat(absent.calls).isZero();
    }

    @Test
    @DisplayName("an out-of-range length from the firmware is treated as missing")
    void corruptLength() {
        firmware.forcedReadLength = 64;

        assertThat(secrets.read("wifi.ssid", new byte[64])).isEqualTo(-1);
    }

    /** The gateway, as the secure firmware answers it. */
    private static final class FakeFirmware implements Pico2WTrustZoneStore.Transport {
        private final boolean present;
        private final Map<Integer, byte[]> slots = new HashMap<>();
        private boolean refuseWrites;
        private int forcedReadLength = -1;
        private int calls;

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
            if (forcedReadLength >= 0) {
                return forcedReadLength;
            }
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
            slots.clear();
            return true;
        }
    }
}
