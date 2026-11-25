package io.github.jabrena.juno.dm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TicTacToeOptimalityTest {
    private static final int BOARD_SIZE = 9;
    private static final int FULL_BOARD = (1 << BOARD_SIZE) - 1;

    @Test
    void referenceModelSelectsAnOptimalMoveForEveryReachablePosition() {
        var positions = ReachablePositions.all();
        var model = ReferenceModel.create(positions);
        var inference = new JevInference(model);
        var features = new float[positions.size()];

        assertThat(positions).hasSize(4520);
        for (int index = 0; index < positions.size(); index++) {
            Position position = positions.get(index);
            features[index] = 1f;
            int selected = inference.evaluate(features, position.legalActions(), position.legalActionCount());
            features[index] = 0f;

            assertThat(position.utility(selected))
                    .as("position %s selected action %d", position, selected)
                    .isEqualTo(position.bestUtility());
        }
    }

    @Test
    void int8QuantizationPreservesOptimalityForEveryReachablePosition() {
        var positions = ReachablePositions.all();
        var model = QuantizedJevModel.from(ReferenceModel.create(positions));
        var inference = new QuantizedJevInference(model);
        var features = new float[positions.size()];

        for (int index = 0; index < positions.size(); index++) {
            Position position = positions.get(index);
            features[index] = 1f;
            int selected = inference.evaluate(features, position.legalActions(), position.legalActionCount());
            features[index] = 0f;

            assertThat(position.utility(selected))
                    .as("position %s selected action %d", position, selected)
                    .isEqualTo(position.bestUtility());
        }
    }

    private record Position(int xMask, int oMask, boolean xTurn) {
        private static final int[][] WINNING_LINES = {
            {0, 1, 2},
            {3, 4, 5},
            {6, 7, 8},
            {0, 3, 6},
            {1, 4, 7},
            {2, 5, 8},
            {0, 4, 8},
            {2, 4, 6}
        };

        int[] legalActions() {
            int[] actions = new int[BOARD_SIZE];
            int count = 0;
            int occupied = xMask | oMask;
            for (int action = 0; action < BOARD_SIZE; action++) {
                if ((occupied & (1 << action)) == 0) {
                    actions[count++] = action;
                }
            }
            return Arrays.copyOf(actions, count);
        }

        int legalActionCount() {
            return BOARD_SIZE - Integer.bitCount(xMask | oMask);
        }

        int utility(int action) {
            if (action < 0 || action >= BOARD_SIZE || ((xMask | oMask) & (1 << action)) != 0) {
                throw new IllegalArgumentException("action is not legal");
            }
            int outcome = outcome(after(action));
            return xTurn ? outcome : -outcome;
        }

        int bestUtility() {
            int best = Integer.MIN_VALUE;
            for (int action : legalActions()) {
                int utility = utility(action);
                best = Math.max(best, utility);
            }
            return best;
        }

        Position after(int action) {
            int bit = 1 << action;
            return xTurn ? new Position(xMask | bit, oMask, false) : new Position(xMask, oMask | bit, true);
        }

        private static int outcome(Position position) {
            if (hasWon(position.xMask)) {
                return 1;
            }
            if (hasWon(position.oMask)) {
                return -1;
            }
            if ((position.xMask | position.oMask) == FULL_BOARD) {
                return 0;
            }
            int best = position.xTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            for (int action : position.legalActions()) {
                int utility = outcome(position.after(action));
                best = position.xTurn ? Math.max(best, utility) : Math.min(best, utility);
            }
            return best;
        }

        private static boolean hasWon(int mask) {
            for (int[] line : WINNING_LINES) {
                if ((mask & (1 << line[0])) != 0
                        && (mask & (1 << line[1])) != 0
                        && (mask & (1 << line[2])) != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ReachablePositions {
        private ReachablePositions() {}

        static List<Position> all() {
            Map<Integer, Position> positions = new LinkedHashMap<>();
            visit(new Position(0, 0, true), positions);
            return new ArrayList<>(positions.values());
        }

        private static void visit(Position position, Map<Integer, Position> positions) {
            if (terminal(position)) {
                return;
            }
            int key = position.xMask | (position.oMask << BOARD_SIZE) | (position.xTurn ? 1 << 18 : 0);
            if (positions.putIfAbsent(key, position) != null) {
                return;
            }
            for (int action : position.legalActions()) {
                visit(position.after(action), positions);
            }
        }

        private static boolean terminal(Position position) {
            return Position.hasWon(position.xMask)
                    || Position.hasWon(position.oMask)
                    || (position.xMask | position.oMask) == FULL_BOARD;
        }
    }

    private static final class ReferenceModel {
        private ReferenceModel() {}

        static JevModel create(List<Position> positions) {
            float[][] weights = new float[BOARD_SIZE][positions.size()];
            for (int feature = 0; feature < positions.size(); feature++) {
                Position position = positions.get(feature);
                for (int action : position.legalActions()) {
                    weights[action][feature] = position.utility(action);
                }
            }
            return new JevModel(weights, new float[BOARD_SIZE]);
        }
    }
}
