package io.github.jabrena.juno.thirdparty.kasa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("TP-Link Kasa protocol framing")
class KasaProtocolTest {

    static Stream<Arguments> rejectedCalls() {
        byte[] frame = new byte[8];
        byte[] command = "abcde".getBytes(StandardCharsets.US_ASCII);
        return Stream.of(
                arguments("a null command byte array",
                        (IntSupplier) () -> KasaProtocol.encode((byte[]) null, 0, frame, frame.length)),
                arguments("a null frame buffer",
                        (IntSupplier) () -> KasaProtocol.encode(command, command.length, null, 8)),
                arguments("a negative command length",
                        (IntSupplier) () -> KasaProtocol.encode(command, -1, frame, frame.length)),
                arguments("a command length longer than the command",
                        (IntSupplier) () -> KasaProtocol.encode(command, 6, frame, frame.length)),
                arguments("no room for the header",
                        (IntSupplier) () -> KasaProtocol.encode(command, command.length, frame, 3)),
                arguments("a frame longer than the buffer",
                        (IntSupplier) () -> KasaProtocol.encode(command, command.length, frame, 9)),
                arguments("a payload that does not fit",
                        (IntSupplier) () -> KasaProtocol.encode(command, command.length, frame, 7)),
                arguments("a null command string",
                        (IntSupplier) () -> KasaProtocol.encode((String) null, frame, frame.length)),
                arguments("a null string frame buffer",
                        (IntSupplier) () -> KasaProtocol.encode("abcde", null, 8)),
                arguments("a string frame with no room for the header",
                        (IntSupplier) () -> KasaProtocol.encode("abcde", frame, 3)),
                arguments("a string frame longer than the buffer",
                        (IntSupplier) () -> KasaProtocol.encode("abcde", frame, 9)),
                arguments("a string payload that does not fit",
                        (IntSupplier) () -> KasaProtocol.encode("abcde", frame, 7)),
                arguments("a null datagram command string",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram((String) null, frame, frame.length)),
                arguments("a null datagram buffer",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram("abcde", null, 8)),
                arguments("a datagram buffer shorter than the command",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram("abcde", frame, 4)),
                arguments("a datagram buffer longer than the buffer",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram("abcde", frame, 9)),
                arguments("a null datagram command",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram((byte[]) null, 0, frame, 8)),
                arguments("a byte datagram buffer shorter than the command",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram(command, command.length, frame, 4)),
                arguments("a negative byte datagram length",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram(command, -1, frame, 8)),
                arguments("a byte datagram length longer than the command",
                        (IntSupplier) () -> KasaProtocol.encodeDatagram(command, 6, frame, 8)),
                arguments("a null reply frame",
                        (IntSupplier) () -> KasaProtocol.decode((byte[]) null, 4)),
                arguments("a reply shorter than its header",
                        (IntSupplier) () -> KasaProtocol.decode(frame, 3)),
                arguments("a reply length longer than the buffer",
                        (IntSupplier) () -> KasaProtocol.decode(frame, 9)),
                arguments("a payload length from a short prefix",
                        (IntSupplier) () -> KasaProtocol.payloadLength(new byte[3])),
                arguments("a null discovery datagram",
                        (IntSupplier) () -> KasaProtocol.decodeDatagram((byte[]) null, 0)),
                arguments("a negative discovery datagram length",
                        (IntSupplier) () -> KasaProtocol.decodeDatagram(frame, -1)),
                arguments("a discovery datagram length longer than the buffer",
                        (IntSupplier) () -> KasaProtocol.decodeDatagram(frame, 9)));
    }

    static Stream<Arguments> discoveryModels() {
        return Stream.of(
                arguments("{\"system\":{\"get_sysinfo\":{\"model\":\"HS100(US)\"}}}", KasaSmartPlug.MODEL_HS100),
                arguments("{\"system\":{\"get_sysinfo\":{\"model\":\"HS110(EU)\"}}}", KasaSmartPlug.MODEL_HS110),
                arguments("{\"system\":{\"get_sysinfo\":{\"model\":\"KP115(US)\"}}}", KasaSmartPlug.MODEL_UNKNOWN));
    }

    static Stream<Arguments> relayStates() {
        return Stream.of(
                arguments("{\"system\":{\"get_sysinfo\":{\"relay_state\":1}}}", KasaSmartPlug.RELAY_ON),
                arguments("{\"system\":{\"get_sysinfo\":{\"relay_state\": 0}}}", KasaSmartPlug.RELAY_OFF),
                arguments("{\"system\":{\"get_sysinfo\":{}}}", KasaSmartPlug.RELAY_UNKNOWN));
    }

    @Test
    @DisplayName("Encoding returns the full frame length")
    void shouldReturnTheFullFrameLengthWhenEncoding() {
        // Given
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[command.length + KasaProtocol.FRAME_HEADER_SIZE];

        // When
        int length = KasaProtocol.encode(command, command.length, frame, frame.length);

        // Then
        assertThat(length).isEqualTo(frame.length);
    }

    @Test
    @DisplayName("Frames commands with their length and the legacy rolling XOR cipher")
    void shouldFrameCommandsWithTheirLengthAndTheLegacyRollingXorCipher() {
        // Given
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] frame = new byte[command.length + KasaProtocol.FRAME_HEADER_SIZE];

        // When
        KasaProtocol.encode(command, command.length, frame, frame.length);

        // Then
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
    @DisplayName("Returns the payload length of a complete reply")
    void shouldReturnThePayloadLengthOfACompleteReply() {
        // Given
        byte[] frame = {
                0, 0, 0, 3,
                (byte) 0xF0, (byte) 0xC0, (byte) 0x9D};

        // When
        int length = KasaProtocol.decode(frame, frame.length);

        // Then
        assertThat(length).isEqualTo(3);
    }

    @Test
    @DisplayName("Decrypts a complete length-prefixed reply")
    void shouldDecryptACompleteLengthPrefixedReply() {
        // Given
        byte[] frame = {
                0, 0, 0, 3,
                (byte) 0xF0, (byte) 0xC0, (byte) 0x9D};
        int length = KasaProtocol.decode(frame, frame.length);

        // When
        String payload = new String(frame, 0, length, StandardCharsets.US_ASCII);

        // Then
        assertThat(payload).isEqualTo("[0]");
    }

    @Test
    @DisplayName("Rejects an incomplete reply")
    void shouldRejectAnIncompleteReply() {
        // Given
        byte[] frame = {
                0, 0, 0, 3,
                (byte) 0xF0, (byte) 0xC0, (byte) 0x9D};

        // When
        int length = KasaProtocol.decode(frame, 6);

        // Then
        assertThat(length).isEqualTo(-1);
    }

    @Test
    @DisplayName("Encodes a discovery datagram with the cipher and no length prefix")
    void shouldEncodeADiscoveryDatagramWithTheCipherAndNoLengthPrefix() {
        // Given
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] datagram = new byte[command.length];

        // When
        KasaProtocol.encodeDatagram(command, command.length, datagram, datagram.length);

        // Then
        assertThat(datagram).startsWith((byte) 0xD0, (byte) 0xF2, (byte) 0x81, (byte) 0xF8);
    }

    @Test
    @DisplayName("Decodes a discovery datagram back to its command")
    void shouldDecodeADiscoveryDatagramBackToItsCommand() {
        // Given
        byte[] command = "{\"system\":{\"get_sysinfo\":null}}".getBytes(StandardCharsets.US_ASCII);
        byte[] datagram = new byte[command.length];
        int length = KasaProtocol.encodeDatagram(command, command.length, datagram, datagram.length);

        // When
        int decoded = KasaProtocol.decodeDatagram(datagram, length);
        String text = new String(datagram, 0, length, StandardCharsets.US_ASCII);

        // Then
        assertThat(decoded).isEqualTo(command.length);
        assertThat(text).isEqualTo("{\"system\":{\"get_sysinfo\":null}}");
    }

    @Test
    @DisplayName("Frames a string command with the same length as its byte array form")
    void shouldFrameAStringCommandWithTheSameLengthAsItsByteArrayForm() {
        // Given
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length() + KasaProtocol.FRAME_HEADER_SIZE];
        byte[] fromBytes = new byte[fromString.length];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);

        // When
        int stringLength = KasaProtocol.encode(command, fromString, fromString.length);
        int bytesLength = KasaProtocol.encode(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        // Then
        assertThat(stringLength).isEqualTo(bytesLength).isEqualTo(fromString.length);
    }

    @Test
    @DisplayName("Frames a string command with the same bytes as its byte array form")
    void shouldFrameAStringCommandWithTheSameBytesAsItsByteArrayForm() {
        // Given
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length() + KasaProtocol.FRAME_HEADER_SIZE];
        byte[] fromBytes = new byte[fromString.length];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);
        KasaProtocol.encode(command, fromString, fromString.length);
        KasaProtocol.encode(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        // When & Then
        assertThat(fromString).containsExactly(fromBytes);
    }

    @Test
    @DisplayName("Encodes a string datagram with the same length as its byte array form")
    void shouldEncodeAStringDatagramWithTheSameLengthAsItsByteArrayForm() {
        // Given
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length()];
        byte[] fromBytes = new byte[command.length()];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);

        // When
        int stringLength = KasaProtocol.encodeDatagram(command, fromString, fromString.length);
        int bytesLength = KasaProtocol.encodeDatagram(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        // Then
        assertThat(stringLength).isEqualTo(command.length());
        assertThat(bytesLength).isEqualTo(stringLength);
    }

    @Test
    @DisplayName("Encodes a string datagram with the same bytes as its byte array form")
    void shouldEncodeAStringDatagramWithTheSameBytesAsItsByteArrayForm() {
        // Given
        String command = "{\"system\":{\"get_sysinfo\":null}}";
        byte[] fromString = new byte[command.length()];
        byte[] fromBytes = new byte[command.length()];
        byte[] commandBytes = command.getBytes(StandardCharsets.US_ASCII);
        KasaProtocol.encodeDatagram(command, fromString, fromString.length);
        KasaProtocol.encodeDatagram(commandBytes, commandBytes.length, fromBytes, fromBytes.length);

        // When & Then
        assertThat(fromString).containsExactly(fromBytes);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("rejectedCalls")
    @DisplayName("Rejects frames that do not fit, are missing, or are malformed")
    void shouldRejectFramesThatDoNotFitAreMissingOrAreMalformed(String description, IntSupplier call) {
        // When & Then
        assertThat(call.getAsInt()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Reads a big-endian length prefix of a reply frame")
    void shouldReadABigEndianLengthPrefixOfAReplyFrame() {
        // When & Then
        assertThat(KasaProtocol.payloadLength(new byte[] {0, 0, 1, 0})).isEqualTo(256);
    }

    @Test
    @DisplayName("Reads a length prefix with the sign bit set as a negative length")
    void shouldReadALengthPrefixWithTheSignBitSetAsANegativeLength() {
        // When & Then
        assertThat(KasaProtocol.payloadLength(new byte[] {(byte) 0xFF, 0, 0, 0})).isEqualTo(-16777216);
    }

    @ParameterizedTest(name = "recognizes model in {0}")
    @MethodSource("discoveryModels")
    @DisplayName("Recognizes only HS100 and HS110 discovery replies")
    void shouldRecognizeOnlyHs100AndHs110DiscoveryReplies(String reply, int expectedModel) {
        // Given
        byte[] bytes = reply.getBytes(StandardCharsets.US_ASCII);

        // When
        int model = KasaSmartPlug.model(bytes, bytes.length);

        // Then
        assertThat(model).isEqualTo(expectedModel);
    }

    @ParameterizedTest(name = "reads relay state from {0}")
    @MethodSource("relayStates")
    @DisplayName("Reads the relay state from discovery replies")
    void shouldReadRelayStateFromDiscoveryReplies(String reply, int expectedState) {
        // Given
        byte[] bytes = reply.getBytes(StandardCharsets.US_ASCII);

        // When
        int state = KasaSmartPlug.relayState(bytes, bytes.length);

        // Then
        assertThat(state).isEqualTo(expectedState);
    }
}
