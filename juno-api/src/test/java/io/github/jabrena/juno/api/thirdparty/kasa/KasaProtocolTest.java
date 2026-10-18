package io.github.jabrena.juno.api.thirdparty.kasa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KasaProtocolTest {
    @Test
    void framesCommandsWithTheirLengthAndTheLegacyRollingXorCipher() {
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[command.length + KasaProtocol.FRAME_HEADER_SIZE];

        int length = KasaProtocol.encode(command, command.length, frame, frame.length);

        assertThat(length).isEqualTo(frame.length);
        assertThat(frame).startsWith(0, 0, 0, command.length);
        assertThat(frame).containsExactly(
                0, 0, 0, 31,
                (byte) 0xD0, (byte) 0xF2, (byte) 0x81, (byte) 0xF8,
                (byte) 0x8B, (byte) 0xFF, (byte) 0x9A, (byte) 0xF7,
                (byte) 0xD5, (byte) 0xEF, (byte) 0x94, (byte) 0xB6,
                (byte) 0xD1, (byte) 0xB4, (byte) 0xC0, (byte) 0x9F,
                (byte) 0xEC, (byte) 0x95, (byte) 0xE6, (byte) 0x8F,
                (byte) 0xE1, (byte) 0x87, (byte) 0xE8, (byte) 0xCA,
                (byte) 0xF0, (byte) 0x9E, (byte) 0xEB, (byte) 0x87,
                (byte) 0xEB, (byte) 0x96, (byte) 0xEB);
    }

    @Test
    void decryptsOnlyACompleteLengthPrefixedReply() {
        byte[] frame = {
                0, 0, 0, 3,
                (byte) 0xF0, (byte) 0xC0, (byte) 0x9D};

        int length = KasaProtocol.decode(frame, frame.length);

        assertThat(length).isEqualTo(3);
        assertThat(new String(frame, 0, length, StandardCharsets.US_ASCII)).isEqualTo("[0]");
        assertThat(KasaProtocol.decode(frame, 6)).isEqualTo(-1);
    }

    @Test
    void encryptsAndDecryptsUnframedUdpDiscoveryDatagrams() {
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] datagram = new byte[command.length];

        int length = KasaProtocol.encodeDatagram(command, command.length, datagram, datagram.length);

        assertThat(length).isEqualTo(command.length);
        assertThat(datagram).startsWith((byte) 0xD0, (byte) 0xF2, (byte) 0x81, (byte) 0xF8);
        assertThat(KasaProtocol.decodeDatagram(datagram, length)).isEqualTo(command.length);
        assertThat(new String(datagram, 0, length, StandardCharsets.US_ASCII)).isEqualTo("{\"system\":{\"get_sysinfo\":null}}");
    }

    @Test
    void recognizesOnlyHs100AndHs110DiscoveryReplies() {
        byte[] hs100 = "{\"system\":{\"get_sysinfo\":{\"model\":\"HS100(US)\"}}}".getBytes(StandardCharsets.US_ASCII);
        byte[] hs110 = "{\"system\":{\"get_sysinfo\":{\"model\":\"HS110(EU)\"}}}".getBytes(StandardCharsets.US_ASCII);
        byte[] other = "{\"system\":{\"get_sysinfo\":{\"model\":\"KP115(US)\"}}}".getBytes(StandardCharsets.US_ASCII);

        assertThat(KasaSmartPlug.model(hs100, hs100.length)).isEqualTo(KasaSmartPlug.MODEL_HS100);
        assertThat(KasaSmartPlug.model(hs110, hs110.length)).isEqualTo(KasaSmartPlug.MODEL_HS110);
        assertThat(KasaSmartPlug.model(other, other.length)).isEqualTo(KasaSmartPlug.MODEL_UNKNOWN);
    }

    @Test
    void readsRelayStateFromDiscoveryReplies() {
        byte[] on = "{\"system\":{\"get_sysinfo\":{\"relay_state\":1}}}".getBytes(StandardCharsets.US_ASCII);
        byte[] off = "{\"system\":{\"get_sysinfo\":{\"relay_state\": 0}}}".getBytes(StandardCharsets.US_ASCII);
        byte[] missing = "{\"system\":{\"get_sysinfo\":{}}}".getBytes(StandardCharsets.US_ASCII);

        assertThat(KasaSmartPlug.relayState(on, on.length)).isEqualTo(KasaSmartPlug.RELAY_ON);
        assertThat(KasaSmartPlug.relayState(off, off.length)).isEqualTo(KasaSmartPlug.RELAY_OFF);
        assertThat(KasaSmartPlug.relayState(missing, missing.length)).isEqualTo(KasaSmartPlug.RELAY_UNKNOWN);
    }
}
