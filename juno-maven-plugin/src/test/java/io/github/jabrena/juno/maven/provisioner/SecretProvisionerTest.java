package io.github.jabrena.juno.maven.provisioner;

import io.github.jabrena.juno.hardware.security.SecretStore;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Secret provisioner protocol")
class SecretProvisionerTest {
    private final FakeStore store = new FakeStore();
    private final SecretProvisioner provisioner = new SecretProvisioner(store);

    @Test
    @DisplayName("SET stores the hex value under its name, in either case, and LIST reports it written")
    void shouldStoreAValueAndListIt() {
        // When
        String set = request("SET wifi.ssid 4a756e4F");
        String listed = request("LIST wifi.ssid");

        // Then
        assertThat(set).isEqualTo("OK wifi.ssid");
        assertThat(store.values.get("wifi.ssid")).isEqualTo("JunO".getBytes(StandardCharsets.US_ASCII));
        assertThat(listed).isEqualTo("STATE wifi.ssid written");
        assertThat(request("LIST wifi.password")).isEqualTo("STATE wifi.password missing");
    }

    @Test
    @DisplayName("SET rejects a missing name, a bad value length and a non-hex digit without storing")
    void shouldRejectABadSet() {
        // When & Then
        assertThat(request("SET  00")).isEqualTo("ERR missing name");
        assertThat(request("SET name")).isEqualTo("ERR missing name");
        assertThat(request("SET name ")).isEqualTo("ERR bad value length");
        assertThat(request("SET name abc")).isEqualTo("ERR bad value length");
        assertThat(request("SET name " + "00".repeat(64))).isEqualTo("ERR bad value length");
        assertThat(request("SET name 0g")).isEqualTo("ERR bad hex value");
        assertThat(request("SET name g0")).isEqualTo("ERR bad hex value");
        assertThat(request("SET name :0")).isEqualTo("ERR bad hex value");
        assertThat(request("SET name !0")).isEqualTo("ERR bad hex value");
        assertThat(store.values).isEmpty();
    }

    @Test
    @DisplayName("SET reports a store that refuses the write")
    void shouldReportARefusedWrite() {
        // Given
        store.refuse = true;

        // When & Then
        assertThat(request("SET name 00")).isEqualTo("ERR store rejected the write: bad name, too long or store full");
    }

    @Test
    @DisplayName("CLEAR empties one name and CLEAR ALL empties the store, each reporting a refused write")
    void shouldClearOneNameOrAll() {
        // Given
        request("SET a 01");
        request("SET b 02");

        // When & Then
        assertThat(request("CLEAR a")).isEqualTo("OK a");
        assertThat(store.values).containsOnlyKeys("b");
        assertThat(request("CLEAR ALL")).isEqualTo("OK CLEAR ALL");
        assertThat(store.values).isEmpty();
        assertThat(request("CLEAR ALLX")).isEqualTo("ERR unknown request");
        store.refuse = true;
        assertThat(request("CLEAR a")).isEqualTo("ERR write failed");
        assertThat(request("CLEAR ALL")).isEqualTo("ERR write failed");
    }

    @Test
    @DisplayName("LIST needs a name, and an unknown or short request is an error")
    void shouldRejectUnknownRequests() {
        // When & Then
        assertThat(request("LIST")).isEqualTo("ERR missing name");
        assertThat(request("LISTx")).isEqualTo("ERR missing name");
        assertThat(request("LIST ")).isEqualTo("ERR missing name");
        assertThat(request("HELLO")).isEqualTo("ERR unknown request");
        assertThat(request("SE")).isEqualTo("ERR unknown request");
        assertThat(request("")).isEqualTo("ERR unknown request");
    }

    @Test
    @DisplayName("Skips carriage returns, ends a request at the newline and rejects an overlong line")
    void shouldReadLines() {
        // When & Then
        assertThat(provisioner.accept('L')).isFalse();
        assertThat(provisioner.accept('\r')).isFalse();
        assertThat(provisioner.accept('\n')).isTrue();
        assertThat(provisioner.answer()).isEqualTo("ERR unknown request");
        assertThat(request("LIST " + "k".repeat(200))).isEqualTo("ERR line too long");
        assertThat(request("LIST k\r")).isEqualTo("STATE k missing");
    }

    /** Sends one request line, as the host does, and returns the board's reply. */
    private String request(String line) {
        for (byte b : line.getBytes(StandardCharsets.US_ASCII)) {
            assertThat(provisioner.accept(b)).isFalse();
        }
        assertThat(provisioner.accept('\n')).isTrue();
        return provisioner.answer();
    }

    /** An in-memory store that can be told to refuse every write. */
    private static final class FakeStore implements SecretStore {
        private final Map<String, byte[]> values = new HashMap<>();
        private boolean refuse;

        @Override
        public int read(String key, byte[] buffer) {
            byte[] value = values.get(key);
            if (value == null) {
                return -1;
            }
            System.arraycopy(value, 0, buffer, 0, value.length);
            return value.length;
        }

        @Override
        public int write(String key, byte[] value, int length) {
            if (refuse) {
                return -1;
            }
            values.put(key, Arrays.copyOf(value, length));
            return length;
        }

        @Override
        public int clear(String key) {
            if (refuse) {
                return -1;
            }
            values.remove(key);
            return 0;
        }

        @Override
        public int clearAll() {
            if (refuse) {
                return -1;
            }
            values.clear();
            return 0;
        }

        @Override
        public @Nullable String getString(String key) {
            byte[] value = values.get(key);
            return value == null ? null : new String(value, StandardCharsets.US_ASCII);
        }
    }
}
