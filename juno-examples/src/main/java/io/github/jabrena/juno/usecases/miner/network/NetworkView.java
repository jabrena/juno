package io.github.jabrena.juno.usecases.miner.network;

import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.commons.TabBar;
import io.github.jabrena.juno.usecases.miner.services.NodeService;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * What this node can tell about its network and its work: its address, the packets it received, rejected, sent
 * and failed to send, its Wi-Fi restarts and its hash rate. The counters change with every packet, so the view
 * refreshes them once a second rather than on every change.
 */
public final class NetworkView implements View {
    private static final Clock CLOCK = Arduino.getClock();
    static final int REFRESH_MILLIS = 1000;

    private final Navigator navigator;
    private final Miner2State model;
    private final Miner2Header header;
    private final NodeService node;
    private int refreshedAt;
    private int hashesAtRefresh;

    public NetworkView(Navigator navigator, Miner2State model, Miner2Header header, NodeService node) {
        this.navigator = navigator;
        this.model = model;
        this.header = header;
        this.node = node;
    }

    @Override
    public void enter() {
        header.enter();
        TabBar.draw(Miner2Views.NETWORK);
        GameScreen.row(1, 1, TftTouchShield.GRAY);
        TftTouchShield.print("UDP port " + NodeIo.PORT);
        refreshedAt = CLOCK.millis();
        hashesAtRefresh = model.hashes();
        showCounters(-1);
    }

    @Override
    public void render() {
        header.render();
        int now = CLOCK.millis();
        int elapsed = now - refreshedAt;
        if (elapsed < REFRESH_MILLIS) {
            return;
        }
        int hashes = model.hashes();
        int rate = hashesPerSecond(hashes - hashesAtRefresh, elapsed);
        refreshedAt = now;
        hashesAtRefresh = hashes;
        showCounters(rate);
    }

    @Override
    public void touch(int x, int y) {
        if (Miner2Header.isWifi(x, y)) {
            node.toggleWifi();
        } else {
            TabBar.follow(navigator, x, y);
        }
    }

    /** The rate of {@code hashes} tried in {@code elapsedMillis}, in hashes per second. */
    static int hashesPerSecond(int hashes, int elapsedMillis) {
        return elapsedMillis <= 0 ? 0 : (int) (hashes * 1000L / elapsedMillis);
    }

    /** Redraws the address and the counters; a negative {@code rate} is not known yet. */
    private void showCounters(int rate) {
        int[] ip = model.address();
        GameScreen.row(0, 2, TftTouchShield.WHITE);
        // The UNO Q reports 0.0.0.0: its Wi-Fi belongs to Linux, which does not share the address with the MCU.
        TftTouchShield.print("IP " + ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3]);
        GameScreen.row(2, 1, TftTouchShield.WHITE);
        TftTouchShield.print("Received " + model.received());
        TftTouchShield.print("  rejected " + model.ignored());
        GameScreen.row(3, 1, TftTouchShield.WHITE);
        TftTouchShield.print("Sent " + model.sent());
        TftTouchShield.print("  failed " + model.failed());
        GameScreen.row(4, 1, model.restarts() > 0 ? TftTouchShield.YELLOW : TftTouchShield.WHITE);
        TftTouchShield.print("Wi-Fi restarts " + model.restarts());
        GameScreen.row(6, 2, TftTouchShield.CYAN);
        TftTouchShield.print(rate < 0 ? "-- H/s" : rate + " H/s");
        GameScreen.row(7, 1, model.syncing() ? TftTouchShield.YELLOW : TftTouchShield.GREEN);
        TftTouchShield.print(model.syncing() ? "SYNC: catching up, not mining" : "Mining");
    }
}
