package io.github.jabrena.juno.usecases.miner2.commons;

/** View ids registered with Miner 2's controller; the first three are also the indexes of their {@link TabBar} tabs. */
public final class Miner2Views {
    public static final int CHAIN = 0;
    public static final int PEERS = 1;
    public static final int NETWORK = 2;
    /** The cover shown at startup; it has no tab. */
    public static final int COVER = 3;

    private Miner2Views() {
    }
}
