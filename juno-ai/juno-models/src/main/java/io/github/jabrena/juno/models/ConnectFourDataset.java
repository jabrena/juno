package io.github.jabrena.juno.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unfinished Connect Four positions labelled with the oracle's move, collected from seeded games.
 *
 * <p>Connect Four has about 4.5 trillion positions, so unlike tic-tac-toe no dataset can hold them all. Each game
 * follows the oracle's move, except that with probability {@code exploration} it plays a random legal column instead,
 * so the games cover the kinds of positions an imperfect opponent produces. A position reached twice is kept once, in
 * the order it was first seen; {@link Random} with a fixed seed makes the dataset identical on every JDK.
 */
public final class ConnectFourDataset {
    private final List<Labelled> examples;

    private ConnectFourDataset(List<Labelled> examples) {
        this.examples = List.copyOf(examples);
    }

    /** A position and the column the oracle chose for it. */
    public record Labelled(ConnectFourPosition position, int action) {
    }

    public static ConnectFourDataset generate(ConnectFourOracle oracle, long seed, int games, double exploration) {
        if (games <= 0 || exploration < 0 || exploration > 1) {
            throw new IllegalArgumentException("games must be positive and exploration between 0 and 1");
        }
        Random random = new Random(seed);
        Map<ConnectFourPosition, Integer> labels = new LinkedHashMap<>();
        for (int game = 0; game < games; game++) {
            ConnectFourPosition position = ConnectFourPosition.empty();
            while (!position.isTerminal()) {
                int label = labels.computeIfAbsent(position, oracle::bestAction);
                int[] legal = position.legalActions();
                int move = random.nextDouble() < exploration ? legal[random.nextInt(legal.length)] : label;
                position = position.play(move);
            }
        }
        List<Labelled> examples = new ArrayList<>(labels.size());
        labels.forEach((position, action) -> examples.add(new Labelled(position, action)));
        return new ConnectFourDataset(examples);
    }

    public List<Labelled> examples() {
        return examples;
    }

    public int size() {
        return examples.size();
    }
}
