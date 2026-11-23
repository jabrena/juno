package io.github.jabrena.juno.language.concurrency;

import java.util.concurrent.Phaser;

/** A reusable phase barrier with a {@link Phaser}, run on the JVM and on the board alike. */
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
            // Not completed[index]++, which compiles to dup2, outside Juno's bytecode subset.
            completed[index] = completed[index] + 1;
            phaser.arriveAndAwaitAdvance();
        }
    }
}
