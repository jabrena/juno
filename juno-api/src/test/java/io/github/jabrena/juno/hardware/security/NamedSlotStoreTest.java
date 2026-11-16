package io.github.jabrena.juno.hardware.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NamedSlotStoreTest {

    private InMemoryStore secrets;

    @BeforeEach
    void setUp() {
        secrets = new InMemoryStore(true);
    }

    @Test
    @DisplayName("a value written under a name reads back by that name alone")
    void writeThenRead() {
        byte[] value = ascii("MyNetwork");
        byte[] buffer = new byte[63];

        assertThat(secrets.write("wifi.ssid", value, value.length)).isEqualTo(value.length);

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(value.length);
        assertThat(new String(buffer, 0, value.length, StandardCharsets.US_ASCII)).isEqualTo("MyNetwork");
        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("the slot keeps the name next to the value, and a new name takes the first empty slot")
    void recordLayout() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        secrets.write("wifi.password", ascii("secret-pass"), 11);

        assertThat(secrets.slots).containsOnlyKeys(0, 1);
        assertThat(secrets.slots.get(0)).isEqualTo(record("wifi.ssid", "MyNetwork"));
        assertThat(secrets.slots.get(1)).isEqualTo(record("wifi.password", "secret-pass"));
    }

    @Test
    @DisplayName("a name is found whichever slot holds it")
    void findsAnySlot() {
        secrets.slots.put(12, record("wifi.password", "secret-pass"));

        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");
    }

    @Test
    @DisplayName("rewriting a name replaces its record in place and leaves no byte of the longer value")
    void writeReplacesInPlace() {
        secrets.write("wifi.ssid", ascii("a-long-network-name"), 19);
        secrets.write("wifi.password", ascii("secret-pass"), 11);

        assertThat(secrets.write("wifi.ssid", ascii("short"), 5)).isEqualTo(5);

        assertThat(secrets.getString("wifi.ssid")).isEqualTo("short");
        assertThat(secrets.slots).containsOnlyKeys(0, 1);
        assertThat(secrets.slots.get(0)).isEqualTo(record("wifi.ssid", "short"));
    }

    @Test
    @DisplayName("a name that was never written reads as missing")
    void missingName() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);

        assertThat(secrets.read("wifi.unknown", new byte[63])).isEqualTo(-1);
        assertThat(secrets.getString("wifi.unknown")).isNull();
    }

    @Test
    @DisplayName("a name that only shares a prefix with a stored one does not match it")
    void prefixDoesNotMatch() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);

        assertThat(secrets.getString("wifi")).isNull();
        assertThat(secrets.getString("wifi.ssid2")).isNull();
    }

    @Test
    @DisplayName("a value too long for the caller's buffer reads as missing")
    void valueLongerThanBuffer() {
        secrets.write("wifi.ssid", ascii("0123456789"), 10);

        assertThat(secrets.read("wifi.ssid", new byte[4])).isEqualTo(-1);
    }

    @Test
    @DisplayName("a value fills the record up to 62 bytes minus the name, and not one byte more")
    void valueCapacity() {
        String name = "wifi.password";
        int capacity = SecretStore.RECORD_BYTES - 1 - name.length();
        byte[] value = new byte[capacity + 1];
        Arrays.fill(value, (byte) 'x');

        assertThat(secrets.write(name, value, capacity + 1)).isEqualTo(-1);
        assertThat(secrets.write(name, value, capacity)).isEqualTo(capacity);
        assertThat(secrets.read(name, new byte[63])).isEqualTo(capacity);
        assertThat(secrets.slots.get(0)).hasSize(SecretStore.RECORD_BYTES);
    }

    @Test
    @DisplayName("a write with a negative length or past the end of the array is refused")
    void badLengths() {
        assertThat(secrets.write("wifi.ssid", new byte[2], -1)).isEqualTo(-1);
        assertThat(secrets.write("wifi.ssid", new byte[2], 3)).isEqualTo(-1);
        assertThat(secrets.slots).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "wifi ssid", "wifi.ssíd", "0123456789012345678901234567890123"})
    @DisplayName("an empty, spaced, non-ASCII or over-long name is neither written nor read")
    void invalidNames(String name) {
        assertThat(secrets.write(name, new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(secrets.read(name, new byte[63])).isEqualTo(-1);
        assertThat(secrets.clear(name)).isEqualTo(-1);
        assertThat(secrets.slots).isEmpty();
    }

    @Test
    @DisplayName("once every slot holds a name a new one is refused, and clearing one makes room")
    void storeFull() {
        for (int i = 0; i < NamedSlotStore.SLOT_COUNT; i++) {
            assertThat(secrets.write("name." + i, new byte[] {1}, 1)).isEqualTo(1);
        }

        assertThat(secrets.write("one.too.many", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(secrets.write("name.3", new byte[] {2}, 1)).isEqualTo(1);

        assertThat(secrets.clear("name.7")).isZero();
        assertThat(secrets.write("one.too.many", new byte[] {1}, 1)).isEqualTo(1);
        assertThat(secrets.slots.get(7)).isEqualTo(record("one.too.many", "\u0001"));
    }

    @Test
    @DisplayName("clear empties one name, leaves the others alone, and succeeds for a name that was not stored")
    void clearOneName() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        secrets.write("wifi.password", ascii("secret-pass"), 11);

        assertThat(secrets.clear("wifi.ssid")).isZero();
        assertThat(secrets.clear("wifi.unknown")).isZero();

        assertThat(secrets.getString("wifi.ssid")).isNull();
        assertThat(secrets.getString("wifi.password")).isEqualTo("secret-pass");
        assertThat(secrets.cleared).containsExactly(0);
    }

    @Test
    @DisplayName("a slot that holds no record, such as a raw value from before names were stored, is ignored and kept")
    void rawValueIsIgnored() {
        byte[] raw = ascii("MyNetwork");
        secrets.slots.put(0, raw);

        assertThat(secrets.getString("MyNetwork")).isNull();
        assertThat(secrets.write("wifi.ssid", ascii("Other"), 5)).isEqualTo(5);

        assertThat(secrets.slots.get(0)).isSameAs(raw);
        assertThat(secrets.slots.get(1)).isEqualTo(record("wifi.ssid", "Other"));
    }

    @Test
    @DisplayName("a record with no value byte, or a slot longer than a record, is not a match")
    void malformedRecords() {
        secrets.slots.put(0, record("wifi.ssid", ""));
        secrets.forcedLength = 64;

        assertThat(secrets.read("wifi.ssid", new byte[64])).isEqualTo(-1);
        secrets.forcedLength = -1;
        assertThat(secrets.read("wifi.ssid", new byte[64])).isEqualTo(-1);
    }

    @Test
    @DisplayName("clearAll empties every slot, including names this program never wrote")
    void clearAllSlots() {
        secrets.write("wifi.ssid", ascii("MyNetwork"), 9);
        secrets.slots.put(3, record("another.program", "value"));

        assertThat(secrets.clearAll()).isZero();

        assertThat(secrets.slots).isEmpty();
        assertThat(secrets.cleared).hasSize(NamedSlotStore.SLOT_COUNT);
    }

    @Test
    @DisplayName("clearAll reports failure when a slot refuses, and still tries every one")
    void clearAllReportsFailure() {
        secrets.refuseClear = true;

        assertThat(secrets.clearAll()).isEqualTo(-1);
        assertThat(secrets.cleared).hasSize(NamedSlotStore.SLOT_COUNT);
    }

    @Test
    @DisplayName("a refused slot write is reported as a failure")
    void refusedWrite() {
        secrets.refuseWrites = true;

        assertThat(secrets.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("a store that never opened reports every value missing and refuses every write")
    void storeUnavailable() {
        InMemoryStore offline = new InMemoryStore(false);
        offline.slots.put(0, record("wifi.ssid", "MyNetwork"));

        assertThat(offline.read("wifi.ssid", new byte[63])).isEqualTo(-1);
        assertThat(offline.write("wifi.ssid", new byte[] {1}, 1)).isEqualTo(-1);
        assertThat(offline.clear("wifi.ssid")).isEqualTo(-1);
        assertThat(offline.clearAll()).isEqualTo(-1);
        assertThat(offline.calls).isZero();
    }

    static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** The bytes a slot holds for {@code name}: its length, the name, then the value. */
    static byte[] record(String name, String value) {
        byte[] record = new byte[1 + name.length() + value.length()];
        record[0] = (byte) name.length();
        System.arraycopy(ascii(name), 0, record, 1, name.length());
        System.arraycopy(ascii(value), 0, record, 1 + name.length(), value.length());
        return record;
    }

    /** Sixteen slots in memory, as a provider's transport presents them. */
    private static final class InMemoryStore extends NamedSlotStore {
        private final Map<Integer, byte[]> slots = new HashMap<>();
        private final List<Integer> cleared = new ArrayList<>();
        private boolean refuseClear;
        private boolean refuseWrites;
        private int forcedLength = -1;
        private int calls;

        InMemoryStore(boolean available) {
            super(available);
        }

        @Override
        int readSlot(int slot, byte[] buffer, int capacity) {
            calls++;
            byte[] stored = slots.get(slot);
            if (stored == null) {
                return -1;
            }
            System.arraycopy(stored, 0, buffer, 0, Math.min(stored.length, capacity));
            return forcedLength >= 0 ? forcedLength : stored.length;
        }

        @Override
        boolean writeSlot(int slot, byte[] bytes, int length) {
            calls++;
            if (refuseWrites) {
                return false;
            }
            slots.put(slot, Arrays.copyOf(bytes, length));
            return true;
        }

        @Override
        boolean clearSlot(int slot) {
            calls++;
            cleared.add(slot);
            if (refuseClear) {
                return false;
            }
            slots.remove(slot);
            return true;
        }
    }
}
