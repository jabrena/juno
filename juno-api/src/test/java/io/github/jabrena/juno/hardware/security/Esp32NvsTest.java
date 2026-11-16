package io.github.jabrena.juno.hardware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.jabrena.juno.hardware.security.NamedSlotStoreTest.ascii;
import static io.github.jabrena.juno.hardware.security.NamedSlotStoreTest.record;
import static org.assertj.core.api.Assertions.assertThat;

class Esp32NvsTest {

    private FakeStorage storage;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        secrets = new Esp32Nvs(storage);
    }

    @Test
    @DisplayName("a written value is one NVS entry holding the name and the value, and reads back by name")
    void writeThenRead() {
        assertThat(secrets.write("wifi.ssid", ascii("MyNetwork"), 9)).isEqualTo(9);

        assertThat(storage.entries).containsOnlyKeys(0);
        assertThat(storage.entries.get(0)).isEqualTo(record("wifi.ssid", "MyNetwork"));
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("clear removes the name's NVS entry and leaves the others alone")
    void clearOneName() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        secrets.write("wifi.password", ascii("secret-pass"), 11);

        assertThat(secrets.clear("wifi.ssid")).isZero();

        assertThat(storage.cleared).containsExactly(0);
        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");
    }

    @Test
    @DisplayName("clearAll removes every entry of the namespace")
    void clearAllSlots() {
        storage.entries.put(3, record("another.program", "value"));

        assertThat(secrets.clearAll()).isZero();

        assertThat(storage.entries).isEmpty();
        for (int slot = 0; slot < NamedSlotStore.SLOT_COUNT; slot++) {
            assertThat(storage.cleared).contains(slot);
        }
    }

    @Test
    @DisplayName("storage that never opened reports every value missing and refuses to clear")
    void storageUnavailable() {
        storage.opened = false;
        SecretStore offline = new Esp32Nvs(storage);

        assertThat(offline.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(offline.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(offline.clearAll()).isEqualTo(-1);
    }

    /** An in-memory NVS namespace: one byte-array entry per slot, as the Preferences shim presents it. */
    private static final class FakeStorage implements Esp32Nvs.Transport {
        private final Map<Integer, byte[]> entries = new HashMap<>();
        private final List<Integer> cleared = new ArrayList<>();
        private boolean opened = true;

        @Override
        public boolean begin() {
            return opened;
        }

        @Override
        public int read(int slot, byte[] buffer, int capacity) {
            byte[] stored = entries.get(slot);
            if (stored == null || stored.length > capacity) {
                return -1;
            }
            System.arraycopy(stored, 0, buffer, 0, stored.length);
            return stored.length;
        }

        @Override
        public boolean write(int slot, byte[] value, int length) {
            byte[] stored = new byte[length];
            System.arraycopy(value, 0, stored, 0, length);
            entries.put(slot, stored);
            return true;
        }

        @Override
        public boolean clear(int slot) {
            cleared.add(slot);
            entries.remove(slot);
            return true;
        }
    }
}
