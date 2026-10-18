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
    void framesAStringLiteralCommandLikeItsByteArrayForm() {
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length() + KasaProtocol.FRAME_HEADER_SIZE];
        byte[] fromBytes = new byte[fromString.length];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);

        int stringLength = KasaProtocol.encode(command, fromString, fromString.length);
        int bytesLength = KasaProtocol.encode(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        assertThat(stringLength).isEqualTo(bytesLength).isEqualTo(fromString.length);
        assertThat(fromString).containsExactly(fromBytes);
    }

    @Test
    void encodesAStringDatagramLikeItsByteArrayForm() {
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length()];
        byte[] fromBytes = new byte[command.length()];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);

        int stringLength = KasaProtocol.encodeDatagram(command, fromString, fromString.length);
        int bytesLength = KasaProtocol.encodeDatagram(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        assertThat(stringLength).isEqualTo(command.length());
        assertThat(bytesLength).isEqualTo(stringLength);
        assertThat(fromString).containsExactly(fromBytes);
    }

    @Test
    void rejectsFramesThatDoNotFitOrAreMissing() {
        byte[] frame = new byte[8];
        byte[] command = "abcde".getBytes(StandardCharsets.US_ASCII);

        assertThat(KasaProtocol.encode((byte[]) null, 0, frame, frame.length)).isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, command.length, null, 8)).isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, -1, frame, frame.length)).isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, 6, frame, frame.length)).as("longer than the command").isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, command.length, frame, 3)).as("no room for the header").isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, command.length, frame, 9)).as("frame longer than the buffer").isEqualTo(-1);
        assertThat(KasaProtocol.encode(command, command.length, frame, 7)).as("payload does not fit").isEqualTo(-1);

        assertThat(KasaProtocol.encode((String) null, frame, frame.length)).isEqualTo(-1);
        assertThat(KasaProtocol.encode("abcde", null, 8)).isEqualTo(-1);
        assertThat(KasaProtocol.encode("abcde", frame, 3)).isEqualTo(-1);
        assertThat(KasaProtocol.encode("abcde", frame, 9)).isEqualTo(-1);
        assertThat(KasaProtocol.encode("abcde", frame, 7)).isEqualTo(-1);

        assertThat(KasaProtocol.encodeDatagram((String) null, frame, frame.length)).isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram("abcde", null, 8)).isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram("abcde", frame, 4)).as("shorter than the command").isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram("abcde", frame, 9)).as("longer than the buffer").isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram((byte[]) null, 0, frame, 8)).isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram(command, command.length, frame, 4)).isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram(command, -1, frame, 8)).isEqualTo(-1);
        assertThat(KasaProtocol.encodeDatagram(command, 6, frame, 8)).isEqualTo(-1);

        assertThat(KasaProtocol.decode(null, 4)).isEqualTo(-1);
        assertThat(KasaProtocol.decode(frame, 3)).isEqualTo(-1);
        assertThat(KasaProtocol.decode(frame, 9)).isEqualTo(-1);
        assertThat(KasaProtocol.payloadLength(new byte[3])).isEqualTo(-1);
        assertThat(KasaProtocol.decodeDatagram(null, 0)).isEqualTo(-1);
        assertThat(KasaProtocol.decodeDatagram(frame, -1)).isEqualTo(-1);
        assertThat(KasaProtocol.decodeDatagram(frame, 9)).isEqualTo(-1);
    }

    @Test
    void readsTheLengthPrefixOfAReplyFrame() {
        assertThat(KasaProtocol.payloadLength(new byte[] {0, 0, 1, 0})).isEqualTo(256);
        assertThat(KasaProtocol.payloadLength(new byte[] {(byte) 0xFF, 0, 0, 0})).isEqualTo(-16777216);
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
