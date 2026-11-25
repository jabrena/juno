package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModelCodec;
import io.github.jabrena.juno.dm.SharedWeightJevTrainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The reproducible JEV1 artifact for the trained Connect Four model, {@code dm/CONNECT_FOUR.JEV}. */
public final class ConnectFourModelArtifact {
    /** The oracle's search depth for the training labels. */
    public static final int ORACLE_DEPTH = 6;
    static final long SEED = 1L;
    static final int GAMES = 400;
    static final double EXPLORATION = 0.25;
    static final int EPOCHS = 20;

    private ConnectFourModelArtifact() {
    }

    /** The training dataset: seeded games labelled by the depth-{@value #ORACLE_DEPTH} oracle. */
    public static ConnectFourDataset dataset() {
        return ConnectFourDataset.generate(new ConnectFourOracle(ORACLE_DEPTH), SEED, GAMES, EXPLORATION);
    }

    /** Trains, quantizes and encodes the model from {@link #dataset()}. */
    public static byte[] encoded() {
        var factory = new ConnectFourModelFactory(
                new ConnectFourFeatures(),
                new SharedWeightJevTrainer(ConnectFourPosition.COLUMNS, ConnectFourFeatures.PER_COLUMN, EPOCHS));
        return new JevModelCodec().write(factory.quantizedModel(dataset()));
    }

    /** Writes the artifact to the path in {@code arguments[0]}. */
    public static void main(String[] arguments) throws IOException {
        Files.write(Path.of(arguments[0]), encoded());
    }
}
