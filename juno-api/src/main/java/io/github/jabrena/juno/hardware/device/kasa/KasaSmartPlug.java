package io.github.jabrena.juno.hardware.device.kasa;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Local control and discovery for legacy TP-Link Kasa HS100 and HS110 plugs, over the JDK's own socket classes.
 *
 * <p>Open a {@link DatagramSocket} with {@link #openDiscovery()} once after Wi-Fi connects, and build a broadcast
 * {@link DatagramPacket} with {@link #discoveryRequest(byte[])} once. Then {@link #broadcastDiscovery} and poll
 * {@link #receiveDiscovery} for each device's reply: its sender is {@link DatagramPacket#getAddress()}, the device's
 * address to pass to {@link #turnOn} or {@link #turnOff}. Every buffer belongs to the caller and can be allocated once
 * before the program's main loop. A control command opens a {@link Socket} to the device for that one exchange.
 */
public final class KasaSmartPlug {
    /** Legacy Kasa LAN TCP and UDP port. */
    public static final int PORT = 9999;
    /** Size needed for the encoded discovery request. */
    public static final int DISCOVERY_BUFFER_SIZE = 31;
    /** Length of the longest TCP command ({@code set_relay_state}), unencrypted. */
    private static final int MAX_COMMAND_LENGTH = 42;
    /** Size needed for the length-prefixed TCP request buffer. */
    public static final int REQUEST_BUFFER_SIZE = MAX_COMMAND_LENGTH + KasaProtocol.FRAME_HEADER_SIZE;
    /** A practical caller-owned response buffer size for an HS100/HS110 system-information reply. */
    public static final int DEFAULT_RESPONSE_BUFFER_SIZE = 1024;
    /** The discovery reply does not identify an HS100 or HS110. */
    public static final int MODEL_UNKNOWN = 0;
    /** The discovery reply identifies a TP-Link Kasa HS100 plug. */
    public static final int MODEL_HS100 = 100;
    /** The discovery reply identifies a TP-Link Kasa HS110 plug. */
    public static final int MODEL_HS110 = 110;
    /** The discovery or system-information reply does not contain a relay state. */
    public static final int RELAY_UNKNOWN = -1;
    /** The discovery or system-information reply identifies a relay that is off. */
    public static final int RELAY_OFF = 0;
    /** The discovery or system-information reply identifies a relay that is on. */
    public static final int RELAY_ON = 1;

    private static final String SYSTEM_INFO = "{\"system\":{\"get_sysinfo\":null}}";
    private static final String TURN_ON = "{\"system\":{\"set_relay_state\":{\"state\":1}}}";
    private static final String TURN_OFF = "{\"system\":{\"set_relay_state\":{\"state\":0}}}";
    private static final int RESPONSE_TIMEOUT_MILLIS = 5000;

    private KasaSmartPlug() {
    }

    /** Opens the UDP socket Kasa device discovery uses; throws when the port is already taken. */
    public static DatagramSocket openDiscovery() throws IOException {
        return new DatagramSocket(PORT);
    }

    /**
     * A packet addressed to the local network's broadcast address on {@link #PORT}, over {@code requestBuffer}: the
     * system-information query {@link #broadcastDiscovery} sends with it. Make it once, not in the loop.
     */
    public static DatagramPacket discoveryRequest(byte[] requestBuffer) throws IOException {
        InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        return new DatagramPacket(requestBuffer, requestBuffer.length, broadcast, PORT);
    }

    /** Encrypts the system-information query into {@code request} and broadcasts it, returning its length or {@code -1}. */
    public static int broadcastDiscovery(DatagramSocket socket, DatagramPacket request) {
        int requestLength = KasaProtocol.encodeDatagram(SYSTEM_INFO, request.getData(), request.getData().length);
        if (requestLength < 0) {
            return -1;
        }
        request.setLength(requestLength);
        try {
            socket.send(request);
            return requestLength;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Receives the next discovery reply into {@code reply} and decrypts it in place. Returns the JSON payload length,
     * {@code 0} while no reply waits, or {@code -1} for a failed receive or a malformed reply. The device that sent it
     * is {@link DatagramPacket#getAddress()}.
     */
    public static int receiveDiscovery(DatagramSocket socket, DatagramPacket reply) {
        try {
            socket.receive(reply);
        } catch (IOException e) {
            return -1;
        }
        int replyLength = reply.getLength();
        return replyLength <= 0 ? replyLength : KasaProtocol.decodeDatagram(reply.getData(), replyLength);
    }

    /**
     * Identifies an HS100 or HS110 from a decrypted discovery or system-information reply. Returns
     * {@link #MODEL_UNKNOWN} for every other Kasa/Tapo device or malformed input.
     */
    public static int model(byte[] responseBuffer, int responseLength) {
        if (contains(responseBuffer, responseLength, "HS100")) {
            return MODEL_HS100;
        }
        return contains(responseBuffer, responseLength, "HS110") ? MODEL_HS110 : MODEL_UNKNOWN;
    }

    /** Returns the relay state from a decrypted discovery or system-information reply. */
    public static int relayState(byte[] responseBuffer, int responseLength) {
        int field = indexOf(responseBuffer, responseLength, "\"relay_state\":");
        if (field < 0) {
            return RELAY_UNKNOWN;
        }
        int index = field + "\"relay_state\":".length();
        while (index < responseLength && responseBuffer[index] == ' ') {
            index++;
        }
        if (index >= responseLength) {
            return RELAY_UNKNOWN;
        }
        return responseBuffer[index] == '0' ? RELAY_OFF : responseBuffer[index] == '1' ? RELAY_ON : RELAY_UNKNOWN;
    }

    /** Queries an HS100/HS110's current system information and returns its decrypted JSON response length. */
    public static int getSystemInfo(InetAddress device, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(device, SYSTEM_INFO, requestBuffer, responseBuffer, responseBufferLength);
    }

    /** Turns a discovered HS100/HS110 relay on and returns its decrypted JSON response length. */
    public static int turnOn(InetAddress device, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(device, TURN_ON, requestBuffer, responseBuffer, responseBufferLength);
    }

    /** Turns a discovered HS100/HS110 relay off and returns its decrypted JSON response length. */
    public static int turnOff(InetAddress device, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(device, TURN_OFF, requestBuffer, responseBuffer, responseBufferLength);
    }

    /** One command over a fresh TCP connection: opened, used and closed here, so the caller owns no socket. */
    private static int exchange(InetAddress device, String command, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        int requestLength = KasaProtocol.encode(command, requestBuffer, requestBuffer.length);
        if (requestLength < 0) {
            return -1;
        }
        Socket socket;
        try {
            socket = new Socket(device, PORT);
        } catch (IOException e) {
            return -1;
        }
        int responseLength = exchangeOn(socket, requestBuffer, requestLength, responseBuffer, responseBufferLength);
        closeQuietly(socket);
        return responseLength;
    }

    private static int exchangeOn(Socket socket, byte[] requestBuffer, int requestLength,
            byte[] responseBuffer, int responseBufferLength) {
        try {
            socket.setSoTimeout(RESPONSE_TIMEOUT_MILLIS);
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            output.write(requestBuffer, 0, requestLength);
            input.read(responseBuffer, 0, KasaProtocol.FRAME_HEADER_SIZE);
            int payloadLength = KasaProtocol.payloadLength(responseBuffer);
            if (payloadLength < 0 || payloadLength > responseBufferLength - KasaProtocol.FRAME_HEADER_SIZE) {
                return -1;
            }
            input.read(responseBuffer, KasaProtocol.FRAME_HEADER_SIZE, payloadLength);
            return KasaProtocol.decode(responseBuffer, payloadLength + KasaProtocol.FRAME_HEADER_SIZE);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // The exchange is already over, and its result does not depend on the close.
        }
    }

    private static boolean contains(byte[] value, int valueLength, String text) {
        return indexOf(value, valueLength, text) >= 0;
    }

    private static int indexOf(byte[] value, int valueLength, String text) {
        if (value == null || text == null || valueLength < 0 || valueLength > value.length || text.length() > valueLength) {
            return -1;
        }
        int last = valueLength - text.length();
        for (int offset = 0; offset <= last; offset++) {
            int matched = 0;
            while (matched < text.length() && (value[offset + matched] & 0xFF) == text.charAt(matched)) {
                matched++;
            }
            if (matched == text.length()) {
                return offset;
            }
        }
        return -1;
    }
}
