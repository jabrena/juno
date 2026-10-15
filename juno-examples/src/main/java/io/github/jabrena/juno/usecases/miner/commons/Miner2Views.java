package io.github.jabrena.juno.usecases.miner.commons;

/** View ids registered with Miner 2's controller; the first four are also the indexes of their tabs. */
public final class Miner2Views {
    public static final int BLOCKS = 0;
    public static final int CHAIN = 1;
    public static final int PEERS = 2;
    public static final int NETWORK = 3;
    /** The cover, shown after the intro; it has no tab. */
    public static final int COVER = 4;
    /** The Java logo and board intro, shown once at startup; it hands over to the cover. */
    public static final int INTRO = 5;

    private Miner2Views() {
    }
}
