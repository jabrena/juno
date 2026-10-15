package io.github.jabrena.juno.usecases.miner2;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.ViewRouter;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.miner.BlockPool;
import io.github.jabrena.juno.usecases.miner.NodeId;
import io.github.jabrena.juno.usecases.miner2.blocks.BlocksView;
import io.github.jabrena.juno.usecases.miner2.chain.ChainView;
import io.github.jabrena.juno.usecases.miner2.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner2.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner2.cover.CoverView;
import io.github.jabrena.juno.usecases.miner2.intro.IntroView;
import io.github.jabrena.juno.usecases.miner2.network.NetworkView;
import io.github.jabrena.juno.usecases.miner2.peers.PeersView;
import io.github.jabrena.juno.usecases.miner2.services.Miner2Persistence;
import io.github.jabrena.juno.usecases.miner2.services.NodeService;

import java.util.UUID;

/**
 * Model-view-controller migration of {@link io.github.jabrena.juno.usecases.miner.MinerGame}, following Doom2. The
 * original game remains in {@code usecases.miner}, and its chain, packets and peer table are reused from there; what
 * moves here is the program's structure. A {@link ViewRouter} runs the frame loop; a cover (the Java logo, an
 * Arduino board, then the title and PLAY) leads to the BLOCKS, CHAIN, PEERS and NET tabs, which are views of one
 * {@link Miner2State}; and the node's networking and mining are one {@link NodeService} task that the router advances
 * every frame, from the cover on. Each node claims a bounty bit length no other node is mining before it mines, and
 * BLOCKS shows that board. Everything runs on the main thread, so the program needs no thread stack
 * and no lock, and the UNO R4 WiFi builds without {@code -Djuno.Xss}. Nodes speak the same packets as MinerGame, so
 * the two programs mine on one chain together; MinerGame's nodes simply keep to 40-bit bounties and claim no bits.
 */
@Board({ArduinoUnoQ.class, ArduinoUnoR4WiFi.class})
public final class Miner2 {
    private static final int INITIAL_BITS = 12;
    private static final int RETARGET_INTERVAL = 8;
    private static final int TARGET_SECONDS = 30;
    /** A frame is a little longer than the node service's mining budget, so touches and redraws stay prompt. */
    private static final int FRAME_MILLIS = 25;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Miner2 example");

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT);

        new Miner2().run();
    }

    private void run() {

        Miner2Persistence persistence = new Miner2Persistence();
        UUID uuid = persistence.loadOrCreateIdentity();
        Miner2State model = new Miner2State(new BlockPool(INITIAL_BITS, RETARGET_INTERVAL, TARGET_SECONDS), uuid);
        int color = persistence.loadColor(BitBoard.COLORS);
        if (color < 0) {
            color = BitBoard.defaultColor(model.nodeId());
            persistence.saveColor(color);
        }
        model.board().publishOwnColor(color);
        model.publishSdCardReady(persistence.isReady());
        Serial.print("Miner2 node ");
        NodeId.print(model.uuidBytes());
        Serial.println("");
        Serial.println(persistence.isReady() ? "SD journal ready" : "SD journal unavailable");

        NodeService node = new NodeService(model, persistence);
        node.connect();
        Miner2Header header = new Miner2Header(model);

        ViewRouter controller = new ViewRouter(FRAME_MILLIS);
        controller.register(Miner2Views.BLOCKS, new BlocksView(controller, model, header, node));
        controller.register(Miner2Views.CHAIN, new ChainView(controller, model, header, node));
        controller.register(Miner2Views.PEERS, new PeersView(controller, model, header, node));
        controller.register(Miner2Views.NETWORK, new NetworkView(controller, model, header, node));
        controller.register(Miner2Views.COVER, new CoverView(controller, node, header));
        controller.register(Miner2Views.INTRO, new IntroView(controller));
        controller.start(Miner2Views.INTRO);
        controller.background(node);
        controller.run();
    }
}
