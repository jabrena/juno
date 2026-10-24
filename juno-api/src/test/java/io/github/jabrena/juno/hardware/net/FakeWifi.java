package io.github.jabrena.juno.hardware.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Test-only stand-in for the board's {@link Wifi}. An access point comes up listening; a join succeeds or fails in the
 * order {@link #joinOutcomes} gives, and each attempt is recorded in {@link #joins} as {@code ssid|password}.
 */
final class FakeWifi implements Wifi {
    /** Each attempted join, as {@code ssid|password}, in order. */
    final List<String> joins = new ArrayList<>();
    /** Whether each join attempt succeeds, consumed in order; an empty queue means the join fails. */
    final Deque<Boolean> joinOutcomes = new ArrayDeque<>();
    /** Whether the board already has a connection when its access point starts. */
    boolean connectedWhileAccessPoint;
    /** How many {@link #status} reads report idle before the real status is reported. */
    int warmupReads;
    String accessPointSsid;
    int status;
    final int[] accessPointAddress = {192, 168, 4, 1};
    final int[] stationAddress = {192, 168, 1, 42};

    @Override
    public void begin(String ssid, String password) {
        joins.add(ssid + "|" + password);
        if (Boolean.TRUE.equals(joinOutcomes.poll())) {
            status = STATUS_CONNECTED;
        } else {
            status = 0;
        }
    }

    @Override
    public void beginAP(String ssid, String password) {
        accessPointSsid = ssid;
        status = connectedWhileAccessPoint ? STATUS_CONNECTED : STATUS_AP_LISTENING;
    }

    @Override
    public int status() {
        if (warmupReads > 0) {
            warmupReads--;
            return 0;
        }
        return status;
    }

    @Override
    public void localIP(int[] octets) {
        int[] address = status == STATUS_AP_LISTENING ? accessPointAddress : stationAddress;
        System.arraycopy(address, 0, octets, 0, address.length);
    }

    @Override
    public void gatewayIP(int[] octets) {
        octets[0] = 192;
        octets[1] = 168;
        octets[2] = 1;
        octets[3] = 1;
    }

    @Override
    public void macAddress(int[] octets) {
        for (int index = 0; index < octets.length; index++) {
            octets[index] = index;
        }
    }
}
