package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProtocolTest {

    @Test
    @DisplayName("a Wi-Fi name accepts up to 31 printable characters and rejects 32")
    void wifiValuesStopAtTheRuntimeStringLimit() {
        char[] fits = "a".repeat(31).toCharArray();
        char[] tooLong = "a".repeat(32).toCharArray();

        assertThat(SecretProtocol.valueBytes("wifi.password", fits)).hasSize(31);
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.password", tooLong))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("at most 31");
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
    @DisplayName("only the names the provisioning program maps are accepted")
    void knownNames() {
        assertThat(SecretProtocol.isKnown("wifi.password")).isTrue();
        assertThat(SecretProtocol.isKnown("wifi.unknown")).isFalse();
    }
}
