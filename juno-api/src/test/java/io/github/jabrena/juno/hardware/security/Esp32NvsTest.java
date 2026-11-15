package io.github.jabrena.juno.hardware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Esp32NvsTest {

    private static final int SSID_SLOT = 8;
    private static final int PASSWORD_SLOT = 9;
    private static final SecretSlots TABLE = new SecretSlots().add("wifi.ssid", SSID_SLOT)
            .add("wifi.password", PASSWORD_SLOT);

    private FakeStorage storage;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        secrets = new Esp32Nvs(TABLE, storage);
    }

    @Test
    @DisplayName("a written value reads back from its own slot at its own length")
    void writeThenRead() {
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[63];

        assertThat(secrets.write("wifi.ssid", value, value.length)).isEqualTo(value.length);

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(value.length);
        assertThat(new String(buffer, 0, value.length, StandardCharsets.US_ASCII)).isEqualTo("MyNetwork");
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
        assertThat(storage.entries).containsOnlyKeys(SSID_SLOT);
    }

    @Test
    @DisplayName("replacing a value leaves no byte of the longer one it replaced")
    void writeReplaces() {
        byte[] longer = "a-long-network-name".getBytes(StandardCharsets.US_ASCII);
        byte[] shorter = "short".getBytes(StandardCharsets.US_ASCII);
        secrets.write("wifi.ssid", longer, longer.length);

        assertThat(secrets.write("wifi.ssid", shorter, shorter.length)).isEqualTo(shorter.length);

        assertThat(secrets.getString("wifi.ssid")).isEqualTo("short");
    }

    @Test
    @DisplayName("an unknown name is neither written nor read")
    void unknownName() {
        assertThat(secrets.write("wifi.unknown", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(secrets.read("wifi.unknown", new byte[63])).isEqualTo(-1);
        assertThat(secrets.getString("wifi.unknown")).isNull();
    }

    @Test
    @DisplayName("a value too long for the caller's buffer reads as missing")
    void valueLongerThanBuffer() {
        byte[] value = "0123456789".getBytes(StandardCharsets.US_ASCII);
        secrets.write("wifi.ssid", value, value.length);

        assertThat(secrets.read("wifi.ssid", new byte[4])).isEqualTo(-1);
    }

    @Test
    @DisplayName("clear empties one name and leaves the others alone")
    void clearOneName() {
        byte[] ssid = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] password = "secret-pass".getBytes(StandardCharsets.US_ASCII);
        secrets.write("wifi.ssid", ssid, ssid.length);
        secrets.write("wifi.password", password, password.length);

        assertThat(secrets.clear("wifi.ssid")).isZero();

        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");
    }

    @Test
    @DisplayName("clearAll empties every slot, including slots the table does not name")
    void clearAllSlots() {
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        secrets.write("wifi.ssid", value, value.length);
        storage.entries.put(3, "another program's secret".getBytes(StandardCharsets.US_ASCII));

        assertThat(secrets.clearAll()).isZero();

        assertThat(storage.entries).isEmpty();
        for (int slot = 0; slot < SecretSlots.SLOT_COUNT; slot++) {
            assertThat(storage.cleared).contains(slot);
        }
    }

    @Test
    @DisplayName("clearAll reports failure when a slot refuses, and still tries every one")
    void clearAllReportsFailure() {
        storage.refuseClear = true;

        assertThat(secrets.clearAll()).isEqualTo(-1);
        assertThat(storage.cleared).hasSize(SecretSlots.SLOT_COUNT);
    }

    @Test
    @DisplayName("storage that never opened reports every value missing and refuses to clear")
    void storageUnavailable() {
        storage.opened = false;
        SecretStore offline = new Esp32Nvs(TABLE, storage);

        assertThat(offline.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(offline.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(offline.clearAll()).isEqualTo(-1);
    }

    /** An in-memory NVS namespace: one byte-array entry per slot, as the Preferences shim presents it. */
    private static final class FakeStorage implements Esp32Nvs.Transport {
        private final Map<Integer, byte[]> entries = new HashMap<>();
        private final List<Integer> cleared = new ArrayList<>();
        private boolean opened = true;
        private boolean refuseClear;

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
            if (refuseClear) {
                return false;
            }
            entries.remove(slot);
            return true;
        }
    }
}
