package io.github.jabrena.juno.models;

import java.util.HashMap;
import java.util.Map;

/** Exhaustive minimax oracle for tic-tac-toe, using X win, draw and O win as 1, 0 and -1. */
public final class TicTacToeOracle {
    private final Map<TicTacToePosition, Integer> outcomes = new HashMap<>();

    public int utility(TicTacToePosition position, int action) {
        int outcome = outcome(position.play(action));
        return position.xTurn() ? outcome : -outcome;
    }

    public int bestUtility(TicTacToePosition position) {
        int best = Integer.MIN_VALUE;
        for (int action : position.legalActions()) {
            best = Math.max(best, utility(position, action));
        }
        return best;
    }

    private int outcome(TicTacToePosition position) {
        if (TicTacToePosition.hasWon(position.xMask())) {
            return 1;
        }
        if (TicTacToePosition.hasWon(position.oMask())) {
            return -1;
        }
        if (position.isTerminal()) {
            return 0;
        }
        Integer cached = outcomes.get(position);
        if (cached != null) {
            return cached;
        }
        int computed = computeOutcome(position);
        outcomes.put(position, computed);
        return computed;
    }

    private int computeOutcome(TicTacToePosition position) {
        int best = position.xTurn() ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int action : position.legalActions()) {
            int outcome = outcome(position.play(action));
            best = position.xTurn() ? Math.max(best, outcome) : Math.min(best, outcome);
        }
        return best;
    }
}
