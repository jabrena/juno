package io.github.jabrena.juno.usecases.miner;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.miner.blocks.BlocksView;
import io.github.jabrena.juno.usecases.miner.chain.BlockPool;
import io.github.jabrena.juno.usecases.miner.chain.ChainView;
import io.github.jabrena.juno.usecases.miner.commons.BitBoard;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Header;
import io.github.jabrena.juno.usecases.miner.commons.Miner2State;
import io.github.jabrena.juno.usecases.miner.commons.Miner2Views;
import io.github.jabrena.juno.usecases.miner.cover.CoverView;
import io.github.jabrena.juno.usecases.miner.intro.IntroView;
import io.github.jabrena.juno.usecases.miner.network.NetworkView;
import io.github.jabrena.juno.usecases.miner.peers.NodeId;
import io.github.jabrena.juno.usecases.miner.peers.PeersView;
import io.github.jabrena.juno.usecases.miner.services.Miner2Persistence;
import io.github.jabrena.juno.usecases.miner.services.NodeService;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.ViewRouter;

import java.util.UUID;

/**
 * A leaderless, Bitcoin-style proof-of-work miner, built model-view-controller like Doom: the chain, its packets and
 * the miner are in {@code chain}, the peer table and node identities in {@code peers}. A {@link ViewRouter} runs the
 * frame loop; a cover (the Java logo, an
 * Arduino board, then the title and PLAY) leads to the BLOCKS, CHAIN, PEERS and NET tabs, which are views of one
 * {@link Miner2State}; and the node's networking and mining are one {@link NodeService} task that the router advances
 * every frame, from the cover on. Each node claims a bounty bit length no other node is mining before it mines, and
 * BLOCKS shows that board. Everything runs on the main thread, so the program needs no thread stack
 * and no lock, and the UNO R4 WiFi builds without {@code -Djuno.Xss}.
 */
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
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

        Miner2Persistence persistence = new Miner2Persistence(TftTouchShield.getSdCard());
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
