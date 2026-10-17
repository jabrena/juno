package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.Phaser;

/** JVM-only reusable phase barrier; Juno does not lower {@link Phaser}. */
public final class PhaserExample {
    private static final int PARTICIPANTS = 2;
    private static final int PHASES = 2;

    private PhaserExample() {}

    /** Returns four completed phase visits (two participants crossing two phases). */
    public static int run() throws InterruptedException {
        Phaser phaser = new Phaser(PARTICIPANTS);
        int[] completed = new int[PARTICIPANTS];
        Thread first = Thread.startVirtualThread(() -> participate(phaser, completed, 0));
        Thread second = Thread.startVirtualThread(() -> participate(phaser, completed, 1));
        first.join();
        second.join();
        return completed[0] + completed[1];
    }

    private static void participate(Phaser phaser, int[] completed, int index) {
        for (int phase = 0; phase < PHASES; phase++) {
            completed[index]++;
            phaser.arriveAndAwaitAdvance();
        }
    }
}
