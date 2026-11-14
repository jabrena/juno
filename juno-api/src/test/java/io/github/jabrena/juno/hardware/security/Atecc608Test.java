package io.github.jabrena.juno.hardware.security;

import io.github.jabrena.juno.hardware.i2c.I2c;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Atecc608Test {

    private static final int SSID_SLOT = 8;
    private static final int PASSWORD_SLOT = 9;
    private static final SecretSlots TABLE = new SecretSlots().add("wifi.ssid", SSID_SLOT)
            .add("wifi.password", PASSWORD_SLOT);

    private ScriptedChip chip;
    private SecretStore secrets;

    @BeforeEach
    void setUp() {
        chip = new ScriptedChip();
        secrets = new Atecc608(chip, TABLE).getSecretStore();
    }

    @Test
    @DisplayName("read copies a provisioned value into the caller's buffer and returns its length")
    void readReturnsStoredValue() {
        chip.store(SSID_SLOT, "MyNetwork");
        byte[] buffer = new byte[72];

        int length = secrets.read("wifi.ssid", buffer);

        assertThat(length).isEqualTo(9);
        assertThat(new String(buffer, 0, length, StandardCharsets.US_ASCII)).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("a value longer than one 32-byte block is assembled from both blocks")
    void readSpansBothBlocks() {
        String value = "0123456789abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOP";
        chip.store(PASSWORD_SLOT, value);
        byte[] buffer = new byte[63];

        int length = secrets.read("wifi.password", buffer);

        assertThat(length).isEqualTo(value.length());
        assertThat(new String(buffer, 0, length, StandardCharsets.US_ASCII)).isEqualTo(value);
    }

    @Test
    @DisplayName("getString returns the value as a String")
    void getStringReturnsValue() {
        chip.store(SSID_SLOT, "MyNetwork");

        assertThat(secrets.getString("wifi.ssid")).isEqualTo("MyNetwork");
    }

    @Test
    @DisplayName("an unknown name is reported as missing, without touching the bus")
    void unknownNameIsMissing() {
        byte[] buffer = new byte[16];

        assertThat(secrets.read("wifi.unknown", buffer)).isEqualTo(-1);
        assertThat(secrets.getString("wifi.unknown")).isNull();
        assertThat(chip.commandsSent).isZero();
    }

    @Test
    @DisplayName("an empty slot is reported as missing")
    void emptySlotIsMissing() {
        byte[] buffer = new byte[16];

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(-1);
        assertThat(secrets.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("a value longer than the buffer fails and leaves the buffer untouched")
    void shortBufferFailsWithoutWriting() {
        chip.store(SSID_SLOT, "MyNetwork");
        byte[] buffer = new byte[4];
        buffer[0] = 'x';

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(-1);
        assertThat(buffer[0]).isEqualTo((byte) 'x');
    }

    @Test
    @DisplayName("a response with a bad CRC is reported as missing")
    void badCrcIsMissing() {
        chip.store(SSID_SLOT, "MyNetwork");
        chip.corruptResponses = true;
        byte[] buffer = new byte[16];

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(-1);
        assertThat(secrets.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("a chip that does not acknowledge is reported as missing")
    void absentChipIsMissing() {
        chip.store(SSID_SLOT, "MyNetwork");
        chip.present = false;
        byte[] buffer = new byte[16];

        assertThat(secrets.read("wifi.ssid", buffer)).isEqualTo(-1);
        assertThat(secrets.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("a name that is not in the program's table is missing, even when its slot holds a value")
    void nameOutsideTheTableIsMissing() {
        chip.store(SSID_SLOT, "MyNetwork");
        SecretStore empty = new Atecc608(chip, new SecretSlots()).getSecretStore();
        byte[] buffer = new byte[16];

        assertThat(empty.read("wifi.ssid", buffer)).isEqualTo(-1);
        assertThat(chip.commandsSent).isZero();
    }

    @Test
    @DisplayName("the table rejects a full table, a bad slot and a duplicate name")
    void tableRejectsBadEntries() {
        SecretSlots full = new SecretSlots();
        for (int i = 0; i < SecretSlots.MAX_NAMES; i++) {
            full.add("name" + i, i);
        }
        assertThatThrownBy(() -> full.add("one-too-many", 9))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretSlots().add("a", 16))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretSlots().add("a", 0).add("a", 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a written value reads back unchanged, including a value spanning both blocks")
    void writeThenReadRoundTrip() {
        String value = "0123456789abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOP";
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[63];

        assertThat(secrets.read("wifi.password", buffer)).isEqualTo(-1);
        assertThat(secrets.write("wifi.password", bytes, bytes.length)).isEqualTo(value.length());
        assertThat(secrets.getString("wifi.password")).isEqualTo(value);
    }

    @Test
    @DisplayName("rewriting a name with a shorter value leaves no byte of the older value behind")
    void shorterRewriteClearsTheTail() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] longValue = "a-much-longer-network-name".getBytes(StandardCharsets.US_ASCII);
        byte[] shortValue = "net".getBytes(StandardCharsets.US_ASCII);
        driver.write("wifi.ssid", longValue, longValue.length);

        driver.write("wifi.ssid", shortValue, shortValue.length);

        assertThat(driver.getString("wifi.ssid")).isEqualTo("net");
    }

    @Test
    @DisplayName("writing zero length clears the name, which then reads as missing")
    void zeroLengthClears() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        driver.write("wifi.ssid", value, value.length);

        assertThat(driver.write("wifi.ssid", value, 0)).isZero();
        assertThat(driver.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("a write to an unknown name, a too-long value or a missing chip fails without changing the chip")
    void writeFailsClosed() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] tooLong = new byte[64];

        assertThat(driver.write("wifi.unknown", value, value.length)).isEqualTo(-1);
        assertThat(driver.write("wifi.ssid", tooLong, tooLong.length)).isEqualTo(-1);
        chip.present = false;
        assertThat(driver.write("wifi.ssid", value, value.length)).isEqualTo(-1);
        assertThat(driver.lastFailure()).isEqualTo(Atecc608.FAILURE_UNAVAILABLE);
        chip.present = true;
        assertThat(driver.getString("wifi.ssid")).isNull();
    }

    @Test
    @DisplayName("a chip that reports a write error (for example a locked zone) makes the write fail")
    void chipErrorStatusFailsTheWrite() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] value = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        chip.writeStatus = 0x01;

        assertThat(driver.write("wifi.ssid", value, value.length)).isEqualTo(-1);
        assertThat(driver.lastFailure()).isEqualTo(Atecc608.FAILURE_REJECTED);
        assertThat(driver.lastStatus()).isEqualTo(0x01);
    }

    @Test
    @DisplayName("clear empties one name and leaves the others alone")
    void clearOneName() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] ssid = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] password = "secret-pass".getBytes(StandardCharsets.US_ASCII);
        driver.write("wifi.ssid", ssid, ssid.length);
        driver.write("wifi.password", password, password.length);

        assertThat(driver.clear("wifi.ssid")).isZero();

        assertThat(driver.getString("wifi.ssid")).isNull();
        assertThat(driver.getString("wifi.password")).isEqualTo("secret-pass");
        assertThat(driver.clear("wifi.unknown")).isEqualTo(-1);
    }

    @Test
    @DisplayName("clearAll empties every name in the table")
    void clearAllNames() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        byte[] ssid = "MyNetwork".getBytes(StandardCharsets.US_ASCII);
        byte[] password = "secret-pass".getBytes(StandardCharsets.US_ASCII);
        driver.write("wifi.ssid", ssid, ssid.length);
        driver.write("wifi.password", password, password.length);

        assertThat(driver.clearAll()).isZero();

        assertThat(driver.getString("wifi.ssid")).isNull();
        assertThat(driver.getString("wifi.password")).isNull();
    }

    @Test
    @DisplayName("clearAll reports failure when the chip refuses a write, and still tries every name")
    void clearAllReportsFailure() {
        Atecc608 driver = new Atecc608(chip, TABLE);
        chip.writeStatus = 0x01;

        assertThat(driver.clearAll()).isEqualTo(-1);
        assertThat(chip.commandsSent).isEqualTo(2);
    }

    @Test
    @DisplayName("the CRC-16 of the same bytes is stable and changes with any byte")
    void crcDetectsChanges() {
        byte[] a = {1, 2, 3, 4};
        byte[] b = {1, 2, 3, 5};

        assertThat(Atecc608.crc16(a, 0, a.length)).isEqualTo(Atecc608.crc16(a, 0, a.length));
        assertThat(Atecc608.crc16(a, 0, a.length)).isNotEqualTo(Atecc608.crc16(b, 0, b.length));
    }

    /**
     * A simulated ATECC608 on a scripted bus: it stores 64-byte slots, answers wake and sleep, and answers each Read
     * command frame with a 35-byte response carrying the block and its CRC-16.
     */
    private static final class ScriptedChip implements I2c {
        private final byte[][] slots = new byte[16][64];
        private byte[] pending = new byte[0];
        boolean present = true;
        boolean corruptResponses;
        /** The status byte a Write answers with: 0x00 is success. */
        byte writeStatus;
        int commandsSent;

        /** Stores {@code value} the way provisioning does: a length byte, then the bytes. */
        void store(int slot, String value) {
            slots[slot][0] = (byte) value.length();
            for (int i = 0; i < value.length(); i++) {
                slots[slot][i + 1] = (byte) value.charAt(i);
            }
        }

        @Override
        public void begin() {
        }

        @Override
        public void writeRegister(int address, int register, int value) {
        }

        @Override
        public int readRegister(int address, int register) {
            return -1;
        }

        @Override
        public int readRegister16(int address, int register) {
            return -1;
        }

        @Override
        public int write(int address, byte[] data, int length) {
            if (!present) {
                return -1;
            }
            if (address == 0x00 || length == 0) {
                return 0;
            }
            if (data[0] == 0x03) {
                commandsSent++;
                if (data[2] == 0x12) {
                    answerWrite(data);
                } else {
                    answerRead(data);
                }
            }
            return length;
        }

        @Override
        public int read(int address, byte[] buffer, int length) {
            if (!present || pending.length == 0 || length != pending.length) {
                return -1;
            }
            System.arraycopy(pending, 0, buffer, 0, length);
            pending = new byte[0];
            return length;
        }

        private void answerWrite(byte[] frame) {
            int address = (frame[4] & 0xFF) | ((frame[5] & 0xFF) << 8);
            int slot = address >> 8;
            int offset = ((address & 0xFF) >> 3) * 32;
            System.arraycopy(frame, 6, slots[slot], offset, 32);
            byte[] status = new byte[4];
            status[0] = 4;
            status[1] = writeStatus;
            int crc = Atecc608.crc16(status, 0, 2);
            status[2] = (byte) (crc & 0xFF);
            status[3] = (byte) ((crc >> 8) & 0xFF);
            pending = status;
        }

        private void answerRead(byte[] frame) {
            int address = (frame[4] & 0xFF) | ((frame[5] & 0xFF) << 8);
            int slot = address >> 8;
            int offset = ((address & 0xFF) >> 3) * 32;
            byte[] response = new byte[35];
            response[0] = 35;
            System.arraycopy(slots[slot], offset, response, 1, 32);
            int crc = Atecc608.crc16(response, 0, 33);
            response[33] = (byte) (crc & 0xFF);
            response[34] = (byte) ((crc >> 8) & 0xFF);
            if (corruptResponses) {
                response[33] ^= 0x01;
            }
            pending = response;
        }
    }
}
