package io.github.jabrena.juno.usecases.miner.services;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.util.mvc.Task;

/**
 * Owns the node's work: the controller advances it one step per frame, on the main thread, before the active view
 * renders. A step serves the network first, so the miner always builds on the freshest tip, then mines for the rest
 * of its budget, on the bounty bit length the network let it claim. Views consume only what the two publish in the model. Joining Wi-Fi is stepped too, from boot, so
 * the cover keeps drawing while the node connects; the network and the miner wait until the link is up.
 */
public final class NodeService implements Task {
    private static final Clock CLOCK = MCU.getClock();
    private final Miner2State model;
    private final NetworkService network;
    private final MiningService mining;
    private final ClaimService claims;
    private final Miner2Persistence persistence;
    private final WifiLink link = new WifiLink();
    private boolean started;
    private boolean networkStarted;

    public NodeService(Miner2State model, Miner2Persistence persistence) {
        this.model = model;
        this.persistence = persistence;
        this.claims = new ClaimService(model);
        this.network = new NetworkService(model, link, claims, CLOCK.millis());
        this.mining = new MiningService(model, persistence, claims);
    }

    /**
     * Starts joining Wi-Fi without sending any chain traffic; later steps finish the join and open the UDP listener.
     */
    public void connect() {
        if (link.state() == WifiLink.IDLE) {
            link.start(CLOCK.millis());
        }
    }

    /** Starts chain replication and mining after PLAY, as soon as the Wi-Fi link is up. */
    public void begin() {
        connect();
        started = true;
    }

    /**
     * The Wi-Fi icon's tap: drops the link, which pauses the network and mining, or joins again, which resumes them and
     * announces the node to the network once more.
     */
    public void toggleWifi() {
        if (link.state() == WifiLink.IDLE) {
            link.start(CLOCK.millis());
        } else {
            link.disconnect();
            networkStarted = false;
        }
    }

    @Override
    public void step() {
        int now = CLOCK.millis();
        link.step(now);
        model.publishWifiConnected(link.connected());
        if (!started || !link.ready()) {
            return;
        }
        if (!networkStarted) {
            network.begin(now);
            networkStarted = true;
        }
        network.step(now);
        if (claims.colorChanged()) {
            int color = model.board().ownColor();
            persistence.saveColor(color);
            Serial.print("Color taken, now ");
            Serial.println(color);
        }
        mining.step();
    }
}
