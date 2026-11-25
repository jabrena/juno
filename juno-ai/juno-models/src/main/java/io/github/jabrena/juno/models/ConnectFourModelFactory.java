package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModel;
import io.github.jabrena.juno.dm.JevTrainingExample;
import io.github.jabrena.juno.dm.QuantizedJevModel;
import io.github.jabrena.juno.dm.SharedWeightJevTrainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Trains the Connect Four JEV model: 7 actions, one per column, over {@link ConnectFourFeatures}. The columns share one
 * set of {@value ConnectFourFeatures#PER_COLUMN} weights, because a winning or blocking move means the same in every
 * column.
 */
public final class ConnectFourModelFactory {
    private final ConnectFourFeatures features;
    private final SharedWeightJevTrainer trainer;

    public ConnectFourModelFactory(ConnectFourFeatures features, SharedWeightJevTrainer trainer) {
        this.features = features;
        this.trainer = trainer;
    }

    public JevModel trainedModel(ConnectFourDataset dataset) {
        List<JevTrainingExample> examples = new ArrayList<>(dataset.size());
        for (ConnectFourDataset.Labelled labelled : dataset.examples()) {
            ConnectFourPosition position = labelled.position();
            examples.add(new JevTrainingExample(features.of(position), position.legalActions(), labelled.action()));
        }
        return trainer.train(examples);
    }

    public QuantizedJevModel quantizedModel(ConnectFourDataset dataset) {
        return QuantizedJevModel.from(trainedModel(dataset));
    }
}
