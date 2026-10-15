package io.github.jabrena.juno.usecases.miner2.commons;

/** View ids registered with Miner 2's controller; the first three are also the indexes of their tabs. */
public final class Miner2Views {
    public static final int CHAIN = 0;
    public static final int PEERS = 1;
    public static final int NETWORK = 2;
    /** The cover, shown after the intro; it has no tab. */
    public static final int COVER = 3;
    /** The Java logo and board intro, shown once at startup; it hands over to the cover. */
    public static final int INTRO = 4;

    private Miner2Views() {
    }
}
