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

class Se050C2Test {

    private static final int NAMESPACE = 0x4A554E00;

    private FakeChip chip;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        chip = new FakeChip();
        secrets = new Se050C2(chip);
    }

    @Test
    @DisplayName("a written value is one object in Juno's namespace holding the name and the value")
    void writeThenRead() {
        assertThat(secrets.write("wifi.ssid", ascii("MyNetwork"), 9)).isEqualTo(9);

        assertThat(chip.objects).containsOnlyKeys(NAMESPACE);
        assertThat(chip.objects.get(NAMESPACE)).isEqualTo(record("wifi.ssid", "MyNetwork"));
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("a name is found in any object of the namespace, and objects outside it are never read")
    void findsAnyObjectInNamespace() {
        chip.objects.put(NAMESPACE | 9, record("wifi.password", "secret-pass"));
        chip.objects.put(0x7FFF0001, record("wifi.ssid", "Outside"));

        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");
        assertThat(secrets.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("clearAll deletes every object in the namespace and leaves objects outside it alone")
    void clearAllSlots() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        chip.objects.put(NAMESPACE | 3, record("another.program", "value"));
        int outsideNamespace = 0x7FFF0001;
        chip.objects.put(outsideNamespace, new byte[] {7});

        assertThat(secrets.clearAll()).isZero();

        assertThat(secrets.getString("wifi.ssid")).isNull();
        for (int slot = 0; slot < NamedSlotStore.SLOT_COUNT; slot++) {
            assertThat(chip.objects).doesNotContainKey(NAMESPACE | slot);
            assertThat(chip.cleared).contains(NAMESPACE | slot);
        }
        assertThat(chip.objects).containsKey(outsideNamespace);
    }

    @Test
    @DisplayName("a chip that never started reports every value missing and refuses to clear")
    void chipUnavailable() {
        chip.started = false;
        SecretStore offline = new Se050C2(chip);

        assertThat(offline.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(offline.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(offline.clearAll()).isEqualTo(-1);
    }

    /** An in-memory SE050C2: binary objects addressed by object id, as the native transport presents them. */
    private static final class FakeChip implements Se050C2.Transport {
        private final Map<Integer, byte[]> objects = new HashMap<>();
        private final List<Integer> cleared = new ArrayList<>();
        private boolean started = true;

        @Override
        public boolean begin() {
            return started;
        }

        @Override
        public int read(int objectId, byte[] buffer, int capacity) {
            byte[] stored = objects.get(objectId);
            if (stored == null || stored.length > capacity) {
                return -1;
            }
            System.arraycopy(stored, 0, buffer, 0, stored.length);
            return stored.length;
        }

        @Override
        public boolean write(int objectId, byte[] value, int length) {
            byte[] stored = new byte[length];
            System.arraycopy(value, 0, stored, 0, length);
            objects.put(objectId, stored);
            return true;
        }

        @Override
        public boolean clear(int objectId) {
            cleared.add(objectId);
            objects.remove(objectId);
            return true;
        }
    }
}
