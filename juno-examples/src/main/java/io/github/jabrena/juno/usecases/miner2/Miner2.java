package io.github.jabrena.juno.usecases.miner2;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.ViewRouter;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.blockchain.GameScreen;
import io.github.jabrena.juno.games.blockchain.NodeIo;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.NodeId;
import io.github.jabrena.juno.usecases.miner2.chain.ChainView;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner2.cover.CoverView;
import io.github.jabrena.juno.usecases.miner2.network.NetworkView;
import io.github.jabrena.juno.usecases.miner2.peers.PeersView;
import io.github.jabrena.juno.usecases.miner2.services.NodeService;

/**
 * Model-view-controller migration of {@link io.github.jabrena.juno.usecases.miner.MinerGame}, following Doom2. The
 * original game remains in {@code usecases.miner}, and its chain, packets and peer table are reused from there; what
 * moves here is the program's structure. A {@link ViewRouter} runs the frame loop; a cover (the Java logo, an
 * Arduino board, then the title and PLAY) leads to the CHAIN, PEERS and NET tabs, which are views of one
 * {@link Miner2State}; and the node's networking and mining are one {@link NodeService} task that the router
 * advances every frame, from the cover on. Everything runs on the main thread, so the program needs no thread stack
 * and no lock, and the UNO R4 WiFi builds without {@code -Djuno.Xss}. Nodes speak the same packets as MinerGame, so
 * the two programs mine on one chain together.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Miner2 {
    private static final int INITIAL_BITS = 12;
    private static final int RETARGET_INTERVAL = 8;
    private static final int TARGET_SECONDS = 30;
    /** A frame is a little longer than the node service's mining budget, so touches and redraws stay prompt. */
    private static final int FRAME_MILLIS = 25;

    private Miner2() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        GameScreen.message("CONNECTING WIFI", TftTouchShield.CYAN);
        NodeIo.connect();

        byte[] uuid = new byte[NodeId.SIZE];
        NodeId.generate(uuid);
        Serial.print("Miner2 node ");
        NodeId.print(uuid);
        Serial.println("");

        Miner2State model = new Miner2State(new BlockPool(INITIAL_BITS, RETARGET_INTERVAL, TARGET_SECONDS), uuid);
        ViewRouter controller = new ViewRouter(FRAME_MILLIS);
        controller.register(Miner2Views.CHAIN, new ChainView(controller, model));
        controller.register(Miner2Views.PEERS, new PeersView(controller, model));
        controller.register(Miner2Views.NETWORK, new NetworkView(controller, model));
        controller.register(Miner2Views.COVER, new CoverView(controller));

        NodeService node = new NodeService(model);
        node.begin();
        controller.start(Miner2Views.COVER);
        controller.background(node);
        controller.run();
    }
}
