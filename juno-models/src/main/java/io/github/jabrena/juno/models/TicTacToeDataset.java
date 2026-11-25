package io.github.jabrena.juno.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The exhaustive reachable non-terminal tic-tac-toe dataset. */
public final class TicTacToeDataset {
    private final List<TicTacToePosition> positions;

    private TicTacToeDataset(List<TicTacToePosition> positions) {
        this.positions = List.copyOf(positions);
    }

    public static TicTacToeDataset exhaustive() {
        Map<TicTacToePosition, Boolean> positions = new LinkedHashMap<>();
        visit(TicTacToePosition.empty(), positions);
        return new TicTacToeDataset(new ArrayList<>(positions.keySet()));
    }

    public List<TicTacToePosition> positions() {
        return positions;
    }

    public int size() {
        return positions.size();
    }

    private static void visit(TicTacToePosition position, Map<TicTacToePosition, Boolean> positions) {
        if (position.isTerminal() || positions.putIfAbsent(position, Boolean.TRUE) != null) {
            return;
        }
        for (int action : position.legalActions()) {
            visit(position.play(action), positions);
        }
    }
}
