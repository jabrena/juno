package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModel;
import io.github.jabrena.juno.dm.QuantizedJevModel;

import java.util.List;

/** Builds the deterministic one-hot reference model used to benchmark the JEV runtime. */
public final class TicTacToeModelFactory {
    private static final int ACTION_COUNT = TicTacToePosition.BOARD_SIZE;
    private final TicTacToeOracle oracle;

    public TicTacToeModelFactory(TicTacToeOracle oracle) {
        this.oracle = oracle;
    }

    public JevModel referenceModel(TicTacToeDataset dataset) {
        List<TicTacToePosition> positions = dataset.positions();
        float[][] weights = new float[ACTION_COUNT][positions.size()];
        for (int feature = 0; feature < positions.size(); feature++) {
            TicTacToePosition position = positions.get(feature);
            for (int action : position.legalActions()) {
                weights[action][feature] = oracle.utility(position, action);
            }
        }
        return new JevModel(weights, new float[ACTION_COUNT]);
    }

    public QuantizedJevModel quantizedModel(TicTacToeDataset dataset) {
        return QuantizedJevModel.from(referenceModel(dataset));
    }
}
