package io.github.jabrena.juno.usecases.miner2.network;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner2.commons.TabBar;

/**
 * What this node can tell about its network and its work: its address, the packets it received, rejected, sent
 * and failed to send, its Wi-Fi restarts and its hash rate. The original Miner only logged these on serial. The
 * counters change with every packet, so the view refreshes them once a second rather than on every change.
 */
public final class NetworkView implements View {
    static final int REFRESH_MILLIS = 1000;

    private final Navigator navigator;
    private final Miner2State model;
    private int refreshedAt;
    private int hashesAtRefresh;

    public NetworkView(Navigator navigator, Miner2State model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        GameScreen.header("MINER", TftTouchShield.GREEN);
        TabBar.draw(Miner2Views.NETWORK);
        GameScreen.row(1, 1, TftTouchShield.GRAY);
        TftTouchShield.print("UDP port " + NodeIo.PORT);
        refreshedAt = Clock.millis();
        hashesAtRefresh = model.hashes();
        showCounters(-1);
    }

    @Override
    public void render() {
        int now = Clock.millis();
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
        TabBar.follow(navigator, x, y);
    }

    /** The rate of {@code hashes} tried in {@code elapsedMillis}, in hashes per second. */
    static int hashesPerSecond(int hashes, int elapsedMillis) {
        return elapsedMillis <= 0 ? 0 : (int) (hashes * 1000L / elapsedMillis);
    }

    /** Redraws the address and the counters; a negative {@code rate} is not known yet. */
    private void showCounters(int rate) {
        int[] ip = model.address();
        GameScreen.row(0, 2, TftTouchShield.WHITE);
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
