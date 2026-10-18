package io.github.jabrena.juno.api.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test-only stand-in for the Wi-Fi intrinsics. An access point comes up listening; a join succeeds or fails in the order
 * {@link #joinOutcomes} gives, and each attempt is recorded in {@link #joins} as {@code ssid|password}.
 */
public final class Wifi {
    public static final int STATUS_CONNECTED = 3;
    public static final int STATUS_AP_LISTENING = 7;

    /** Each attempted join, as {@code ssid|password}, in order. */
    public static final List<String> joins = new ArrayList<>();
    /** Whether each join attempt succeeds, consumed in order; an empty queue means the join fails. */
    public static final Deque<Boolean> joinOutcomes = new ArrayDeque<>();
    /** Whether the board already has a connection when its access point starts. */
    public static boolean connectedWhileAccessPoint;
    /** How many {@link #status} reads report idle before the real status is reported. */
    public static int warmupReads;
    public static String accessPointSsid;
    public static int status;
    public static final int[] address = {192, 168, 1, 42};

    private Wifi() {
    }

    public static void reset() {
        joins.clear();
        joinOutcomes.clear();
        connectedWhileAccessPoint = false;
        warmupReads = 0;
        accessPointSsid = null;
        status = 0;
    }

    public static void begin(String ssid, String password) {
        joins.add(ssid + "|" + password);
        if (Boolean.TRUE.equals(joinOutcomes.poll())) {
            status = STATUS_CONNECTED;
        } else {
            status = 0;
        }
    }

    public static void beginAP(String ssid, String password) {
        accessPointSsid = ssid;
        status = connectedWhileAccessPoint ? STATUS_CONNECTED : STATUS_AP_LISTENING;
    }

    public static int status() {
        if (warmupReads > 0) {
            warmupReads--;
            return 0;
        }
        return status;
    }

    public static void localIP(int[] octets) {
        System.arraycopy(address, 0, octets, 0, address.length);
    }

    public static void gatewayIP(int[] octets) {
        octets[0] = 192;
        octets[1] = 168;
        octets[2] = 1;
        octets[3] = 1;
    }

    public static void macAddress(int[] octets) {
        for (int index = 0; index < octets.length; index++) {
            octets[index] = index;
        }
    }
}
