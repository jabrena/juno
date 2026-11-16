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

class AvrEepromTest {

    /** The UNO WiFi Rev2's 256-byte EEPROM holds four 64-byte slots. */
    private static final int REV2_SLOTS = 4;

    private FakeEeprom eeprom;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        eeprom = new FakeEeprom(REV2_SLOTS);
        secrets = new AvrEeprom(eeprom);
    }

    @Test
    @DisplayName("a written value is one EEPROM slot holding the name and the value, and reads back by name")
    void writeThenRead() {
        assertThat(secrets.write("wifi.ssid", ascii("MyNetwork"), 9)).isEqualTo(9);

        assertThat(eeprom.slots).containsOnlyKeys(0);
        assertThat(eeprom.slots.get(0)).isEqualTo(record("wifi.ssid", "MyNetwork"));
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("the store holds as many names as the EEPROM has slots, and no more")
    void holdsOneNamePerSlot() {
        for (int i = 0; i < REV2_SLOTS; i++) {
            assertThat(secrets.write("name." + i, new byte[] {1}, 1)).isEqualTo(1);
        }

        assertThat(secrets.write("one.too.many", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(eeprom.reads).as("a lookup never reads past the EEPROM's last slot").allMatch(slot -> slot < REV2_SLOTS);
    }

    @Test
    @DisplayName("clear empties one slot, and clearAll empties every slot the EEPROM has")
    void clearAndClearAll() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        secrets.write("wifi.password", ascii("secret-pass"), 11);

        assertThat(secrets.clear("wifi.ssid")).isZero();
        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");

        assertThat(secrets.clearAll()).isZero();
        assertThat(eeprom.slots).isEmpty();
        assertThat(eeprom.cleared).contains(0, 1, 2, 3).doesNotContain(4);
    }

    @Test
    @DisplayName("an EEPROM too small for one slot leaves the store unavailable")
    void noSlots() {
        SecretStore none = new AvrEeprom(new FakeEeprom(0));

        assertThat(none.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(none.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(none.clearAll()).isEqualTo(-1);
    }

    /** An in-memory EEPROM split into slots, as the native transport presents it. */
    private static final class FakeEeprom implements AvrEeprom.Transport {
        private final int slotCount;
        private final Map<Integer, byte[]> slots = new HashMap<>();
        private final List<Integer> reads = new ArrayList<>();
        private final List<Integer> cleared = new ArrayList<>();

        FakeEeprom(int slotCount) {
            this.slotCount = slotCount;
        }

        @Override
        public int slotCount() {
            return slotCount;
        }

        @Override
        public int read(int slot, byte[] buffer, int capacity) {
            reads.add(slot);
            byte[] stored = slots.get(slot);
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
            slots.put(slot, stored);
            return true;
        }

        @Override
        public boolean clear(int slot) {
            cleared.add(slot);
            slots.remove(slot);
            return true;
        }
    }
}
