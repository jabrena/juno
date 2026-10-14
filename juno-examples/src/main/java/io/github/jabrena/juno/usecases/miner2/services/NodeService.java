package io.github.jabrena.juno.usecases.miner2.services;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.mvc.Task;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;

/**
 * Owns the node's work: the controller advances it one step per frame, on the main thread, before the active view
 * renders. A step serves the network first, so the miner always builds on the freshest tip, then mines for the rest
 * of its budget. Views consume only what the two publish in the model.
 */
public final class NodeService implements Task {
    private final NetworkService network;
    private final MiningService mining;

    public NodeService(Miner2State model) {
        this.network = new NetworkService(model, Clock.millis());
        this.mining = new MiningService(model);
    }

    /** Asks the network for its tip before the first frame. */
    public void begin() {
        network.begin();
    }

    @Override
    public void step() {
        network.step(Clock.millis());
        mining.step();
    }
}
