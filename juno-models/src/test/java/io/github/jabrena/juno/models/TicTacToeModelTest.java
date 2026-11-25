package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevInference;
import io.github.jabrena.juno.dm.QuantizedJevInference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicTacToeModelTest {
    @Test
    void exhaustiveDatasetContainsEveryReachableNonTerminalPosition() {
        var dataset = TicTacToeDataset.exhaustive();

        assertThat(dataset.size()).isEqualTo(4520);
        assertThat(dataset.positions()).allMatch(position -> !position.isTerminal());
    }

    @Test
    void referenceModelMatchesTheMinimaxOracle() {
        var dataset = TicTacToeDataset.exhaustive();
        var oracle = new TicTacToeOracle();
        var model = new TicTacToeModelFactory(oracle).referenceModel(dataset);
        var inference = new JevInference(model);
        var features = new float[dataset.size()];

        for (int index = 0; index < dataset.size(); index++) {
            TicTacToePosition position = dataset.positions().get(index);
            features[index] = 1f;
            int selected = inference.evaluate(features, position.legalActions(), position.legalActionCount());
            features[index] = 0f;

            assertThat(oracle.utility(position, selected))
                    .as("position %s selected action %d", position, selected)
                    .isEqualTo(oracle.bestUtility(position));
        }
    }

    @Test
    void quantizedReferenceModelPreservesTheMinimaxOracle() {
        var dataset = TicTacToeDataset.exhaustive();
        var oracle = new TicTacToeOracle();
        var model = new TicTacToeModelFactory(oracle).quantizedModel(dataset);
        var inference = new QuantizedJevInference(model);
        var features = new float[dataset.size()];

        for (int index = 0; index < dataset.size(); index++) {
            TicTacToePosition position = dataset.positions().get(index);
            features[index] = 1f;
            int selected = inference.evaluate(features, position.legalActions(), position.legalActionCount());
            features[index] = 0f;

            assertThat(oracle.utility(position, selected))
                    .as("position %s selected action %d", position, selected)
                    .isEqualTo(oracle.bestUtility(position));
        }
    }
}
