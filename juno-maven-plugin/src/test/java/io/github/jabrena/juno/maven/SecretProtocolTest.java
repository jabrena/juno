package io.github.jabrena.juno.maven;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProtocolTest {

    @Test
    @DisplayName("a value shares the 63-byte record with its name, and a longer one is rejected")
    void valuesStopAtTheRecordLimit() {
        char[] fits = "a".repeat(49).toCharArray();
        char[] tooLong = "a".repeat(50).toCharArray();

        assertThat(SecretProtocol.valueBytes("wifi.password", fits)).hasSize(49);
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.password", tooLong))
                .isInstanceOf(ArduinoCliException.class)
                .hasMessageContaining("at most 49 next to that name");
    }

    @Test
    @DisplayName("a shorter name leaves more room for its value")
    void shorterNameLeavesMoreRoom() {
        assertThat(SecretProtocol.maxValueBytes("k")).isEqualTo(61);
        assertThat(SecretProtocol.maxValueBytes("mailbox.apikey")).isEqualTo(48);
        assertThat(SecretProtocol.valueBytes("mailbox.apikey", "k".repeat(48).toCharArray())).hasSize(48);
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

    @Test
    @DisplayName("a name must be 1 to 31 printable non-space characters, and a value printable ASCII")
    void rejectsBadNamesAndControlCharacters() {
        assertThatThrownBy(() -> SecretProtocol.validateName("")).hasMessageContaining("1 to 31 characters");
        assertThatThrownBy(() -> SecretProtocol.validateName("k".repeat(32))).hasMessageContaining("1 to 31");
        assertThatThrownBy(() -> SecretProtocol.validateName("a b")).hasMessageContaining("non-space ASCII");
        assertThatThrownBy(() -> SecretProtocol.validateName("a\u007f")).hasMessageContaining("non-space ASCII");
        assertThatThrownBy(() -> SecretProtocol.valueBytes("wifi.ssid", "a\tb".toCharArray()))
                .hasMessageContaining("printable ASCII");
    }
}
