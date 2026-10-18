package io.github.jabrena.juno.api.thirdparty.kasa;

/** The length-prefixed rolling-XOR wire format used by legacy Kasa LAN devices. */
final class KasaProtocol {
    static final int FRAME_HEADER_SIZE = 4;
    private static final int INITIAL_KEY = 171;

    private KasaProtocol() {
    }

    /** Frames and encrypts {@code command[0, commandLength)} into {@code frame}. */
    static int encode(byte[] command, int commandLength, byte[] frame, int frameLength) {
        if (command == null || frame == null || commandLength < 0 || commandLength > command.length
                || frameLength < FRAME_HEADER_SIZE || frameLength > frame.length
                || commandLength > frameLength - FRAME_HEADER_SIZE) {
            return -1;
        }
        frame[0] = (byte) (commandLength >>> 24);
        frame[1] = (byte) (commandLength >>> 16);
        frame[2] = (byte) (commandLength >>> 8);
        frame[3] = (byte) commandLength;
        encodePayload(command, commandLength, frame, FRAME_HEADER_SIZE);
        return commandLength + FRAME_HEADER_SIZE;
    }

    /** Encrypts a UDP discovery command, whose legacy datagram has no length prefix. */
    static int encodeDatagram(byte[] command, int commandLength, byte[] datagram, int datagramLength) {
        if (command == null || datagram == null || commandLength < 0 || commandLength > command.length
                || datagramLength < commandLength || datagramLength > datagram.length) {
            return -1;
        }
        encodePayload(command, commandLength, datagram, 0);
        return commandLength;
    }

    /** Frames and encrypts an ASCII command held in a flash-resident string literal. */
    static int encode(String command, byte[] frame, int frameLength) {
        if (command == null || frame == null || frameLength < FRAME_HEADER_SIZE || frameLength > frame.length
                || command.length() > frameLength - FRAME_HEADER_SIZE) {
            return -1;
        }
        int commandLength = command.length();
        frame[0] = (byte) (commandLength >>> 24);
        frame[1] = (byte) (commandLength >>> 16);
        frame[2] = (byte) (commandLength >>> 8);
        frame[3] = (byte) commandLength;
        encodePayload(command, frame, FRAME_HEADER_SIZE);
        return commandLength + FRAME_HEADER_SIZE;
    }

    /** Encrypts an ASCII discovery command held in a flash-resident string literal. */
    static int encodeDatagram(String command, byte[] datagram, int datagramLength) {
        if (command == null || datagram == null || datagramLength < command.length() || datagramLength > datagram.length) {
            return -1;
        }
        encodePayload(command, datagram, 0);
        return command.length();
    }

    private static void encodePayload(byte[] command, int commandLength, byte[] target, int offset) {
        int key = INITIAL_KEY;
        for (int index = 0; index < commandLength; index++) {
            key ^= command[index] & 0xFF;
            target[index + offset] = (byte) key;
        }
    }

    private static void encodePayload(String command, byte[] target, int offset) {
        int key = INITIAL_KEY;
        for (int index = 0; index < command.length(); index++) {
            key ^= command.charAt(index) & 0xFF;
            target[index + offset] = (byte) key;
        }
    }

    /** Decrypts a complete frame in place and returns its JSON payload length. */
    static int decode(byte[] frame, int frameLength) {
        if (frame == null || frameLength < FRAME_HEADER_SIZE || frameLength > frame.length) {
            return -1;
        }
        int payloadLength = ((frame[0] & 0xFF) << 24) | ((frame[1] & 0xFF) << 16)
                | ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        if (payloadLength < 0 || payloadLength != frameLength - FRAME_HEADER_SIZE) {
            return -1;
        }
        decodePayload(frame, FRAME_HEADER_SIZE, 0, payloadLength);
        return payloadLength;
    }

    static int payloadLength(byte[] frame) {
        if (frame.length < FRAME_HEADER_SIZE) {
            return -1;
        }
        return ((frame[0] & 0xFF) << 24) | ((frame[1] & 0xFF) << 16) | ((frame[2] & 0xFF) << 8)
                | (frame[3] & 0xFF);
    }

    /** Decrypts a UDP discovery datagram in place. */
    static int decodeDatagram(byte[] datagram, int datagramLength) {
        if (datagram == null || datagramLength < 0 || datagramLength > datagram.length) {
            return -1;
        }
        decodePayload(datagram, 0, 0, datagramLength);
        return datagramLength;
    }

    private static void decodePayload(byte[] source, int sourceOffset, int targetOffset, int length) {
        int key = INITIAL_KEY;
        for (int index = 0; index < length; index++) {
            int encrypted = source[sourceOffset + index] & 0xFF;
            source[targetOffset + index] = (byte) (encrypted ^ key);
            key = encrypted;
        }
    }
}
