package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProtocolTest {

    @Test
    @DisplayName("a secret accepts the secure-element slot capacity and rejects larger values")
    void valuesStopAtTheSlotLimit() {
        char[] fits = "a".repeat(63).toCharArray();
        char[] tooLong = "a".repeat(64).toCharArray();

        assertThat(SecretProtocol.valueBytes("wifi.password", fits)).hasSize(63);
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.password", tooLong))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("at most 63");
    }

    @Test
    @DisplayName("the mailbox key may use the full 63-byte slot")
    void mailboxKeyUsesTheSlotLimit() {
        assertThat(SecretProtocol.valueBytes("mailbox.apikey", "k".repeat(63).toCharArray())).hasSize(63);
        assertThatThrownBy(() -> SecretProtocol.valueBytes("mailbox.apikey", "k".repeat(64).toCharArray()))
                .hasMessageContaining("at most 63");
    }

    @Test
    @DisplayName("an empty value and a non-ASCII character are rejected before any upload")
    void rejectsEmptyAndNonAscii() {
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.ssid", new char[0]))
                .hasMessageContaining("is empty");
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.ssid", "café".toCharArray()))
                .hasMessageContaining("printable ASCII");
    }

    @Test
    @DisplayName("a SET request carries the value as lowercase hex, so no byte can end the line")
    void setRequestIsHex() {
        assertThat(SecretProtocol.setRequest("wifi.ssid", new byte[] {'A', 0x0A, (byte) 0xFF}))
                .isEqualTo("SET wifi.ssid 410aff");
    }

    @Test
    @DisplayName("the protocol does not maintain a board-secret name allowlist")
    void arbitraryNamesCanBeEncoded() {
        assertThat(SecretProtocol.setRequest("board.custom", new byte[] {'o', 'k'}))
                .isEqualTo("SET board.custom 6f6b");
    }
}
