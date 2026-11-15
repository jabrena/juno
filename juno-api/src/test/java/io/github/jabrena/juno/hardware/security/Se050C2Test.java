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

class Se050C2Test {

    private static final int SSID_SLOT = 8;
    private static final int PASSWORD_SLOT = 9;
    private static final int NAMESPACE = 0x4A554E00;
    private static final SecretSlots TABLE = new SecretSlots().add("wifi.ssid", SSID_SLOT)
            .add("wifi.password", PASSWORD_SLOT);

    private FakeChip chip;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        chip = new FakeChip();
        secrets = new Se050C2(TABLE, chip);
    }

    @Test
    @DisplayName("a written value reads back at its own length")
    void writeThenRead() {
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[63];

        assertThat(secrets.write("wifi.ssid", value, value.length)).isEqualTo(value.length);
        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(value.length);
        assertThat(new String(buffer, 0, value.length, StandardCharsets.US_ASCII)).isEqualTo("MyNetwork");
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("an unknown name is neither written nor read")
    void unknownName() {
        byte[] value = {1, 2, 3};

        assertThat(secrets.write("wifi.unknown", value, value.length)).isEqualTo(-1);
        assertThat(secrets.read("wifi.unknown", new byte[63])).isEqualTo(-1);
        assertThat(secrets.getString("wifi.unknown")).isNull();
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
    @DisplayName("clearAll deletes every object in the namespace, including slots the table does not name")
    void clearAllSlots() {
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        secrets.write("wifi.ssid", value, value.length);
        chip.objects.put(NAMESPACE | 3, "another program's secret".getBytes(StandardCharsets.US_ASCII));
        int outsideNamespace = 0x7FFF0001;
        chip.objects.put(outsideNamespace, new byte[] {7});

        assertThat(secrets.clearAll()).isZero();

        assertThat(secrets.getString("wifi.ssid")).isNull();
        for (int slot = 0; slot < SecretSlots.SLOT_COUNT; slot++) {
            assertThat(chip.objects).doesNotContainKey(NAMESPACE | slot);
            assertThat(chip.cleared).contains(NAMESPACE | slot);
        }
        assertThat(chip.objects).containsKey(outsideNamespace);
    }

    @Test
    @DisplayName("clearAll reports failure when the chip refuses a delete, and still tries every slot")
    void clearAllReportsFailure() {
        chip.refuseClear = true;

        assertThat(secrets.clearAll()).isEqualTo(-1);
        assertThat(chip.cleared).hasSize(SecretSlots.SLOT_COUNT);
    }

    @Test
    @DisplayName("a chip that never started reports every value missing and refuses to clear")
    void chipUnavailable() {
        chip.started = false;
        SecretStore offline = new Se050C2(TABLE, chip);

        assertThat(offline.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(offline.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(offline.clearAll()).isEqualTo(-1);
    }

    /** An in-memory SE050C2: binary objects addressed by object id, as the native transport presents them. */
    private static final class FakeChip implements Se050C2.Transport {
        private final Map<Integer, byte[]> objects = new HashMap<>();
        private final List<Integer> cleared = new ArrayList<>();
        private boolean started = true;
        private boolean refuseClear;

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
            if (refuseClear) {
                return false;
            }
            objects.remove(objectId);
            return true;
        }
    }
}
