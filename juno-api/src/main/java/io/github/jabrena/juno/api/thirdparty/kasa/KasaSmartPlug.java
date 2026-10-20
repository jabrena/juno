package io.github.jabrena.juno.api.thirdparty.kasa;

import io.github.jabrena.juno.api.hardware.net.Tcp;
import io.github.jabrena.juno.api.hardware.net.Udp;

/**
 * Allocation-free local control and discovery for legacy TP-Link Kasa HS100 and HS110 plugs.
 *
 * <p>Call {@link #openDiscovery()} once after Wi-Fi connects, then {@link #broadcastDiscovery} and poll {@link
 * #receiveDiscovery} for device endpoints. Pass a discovered endpoint to {@link #turnOn} or {@link #turnOff}; it is
 * an {@link Udp#ENDPOINT_SIZE endpoint} whose first four entries are the plug's IPv4 address. All buffers belong to
 * the caller and can be allocated once before the program's main loop.
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

    /** Opens the single UDP socket Juno uses for Kasa device discovery. */
    public static boolean openDiscovery() {
        return Udp.listen(PORT);
    }

    /** Broadcasts a system-information query and returns the encrypted datagram length, or {@code -1} on failure. */
    public static int broadcastDiscovery(byte[] requestBuffer, int requestBufferLength) {
        int requestLength = KasaProtocol.encodeDatagram(SYSTEM_INFO, requestBuffer, requestBufferLength);
        return requestLength < 0 ? -1 : Udp.broadcast(PORT, requestBuffer, requestLength);
    }

    /**
     * Decrypts the next discovery reply into {@code responseBuffer} and writes its sender to {@code endpoint}.
     * Returns the JSON payload length, {@code 0} while no reply waits, or {@code -1} for invalid input or a malformed
     * reply.
     */
    public static int receiveDiscovery(byte[] responseBuffer, int responseBufferLength, int[] endpoint) {
        int responseLength = Udp.receive(responseBuffer, responseBufferLength, endpoint);
        return responseLength <= 0 ? responseLength : KasaProtocol.decodeDatagram(responseBuffer, responseLength);
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
    public static int getSystemInfo(int[] endpoint, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(endpoint, SYSTEM_INFO, requestBuffer, responseBuffer, responseBufferLength);
    }

    /** Turns a discovered HS100/HS110 relay on and returns its decrypted JSON response length. */
    public static int turnOn(int[] endpoint, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(endpoint, TURN_ON, requestBuffer, responseBuffer, responseBufferLength);
    }

    /** Turns a discovered HS100/HS110 relay off and returns its decrypted JSON response length. */
    public static int turnOff(int[] endpoint, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        return exchange(endpoint, TURN_OFF, requestBuffer, responseBuffer, responseBufferLength);
    }

    private static int exchange(int[] endpoint, String command, byte[] requestBuffer,
            byte[] responseBuffer, int responseBufferLength) {
        int requestLength = KasaProtocol.encode(command, requestBuffer, requestBuffer.length);
        if (requestLength < 0) {
            return -1;
        }
        if (!Tcp.open(endpoint, PORT)) {
            return -1;
        }
        if (Tcp.write(requestBuffer, requestLength) != requestLength
                || Tcp.read(responseBuffer, 0, KasaProtocol.FRAME_HEADER_SIZE, RESPONSE_TIMEOUT_MILLIS)
                != KasaProtocol.FRAME_HEADER_SIZE) {
            Tcp.close();
            return -1;
        }
        int payloadLength = KasaProtocol.payloadLength(responseBuffer);
        if (payloadLength < 0 || payloadLength > responseBufferLength - KasaProtocol.FRAME_HEADER_SIZE
                || Tcp.read(responseBuffer, KasaProtocol.FRAME_HEADER_SIZE, payloadLength, RESPONSE_TIMEOUT_MILLIS)
                != payloadLength) {
            Tcp.close();
            return -1;
        }
        Tcp.close();
        return KasaProtocol.decode(responseBuffer, payloadLength + KasaProtocol.FRAME_HEADER_SIZE);
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
