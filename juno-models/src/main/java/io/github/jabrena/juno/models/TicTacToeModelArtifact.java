package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModelCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The reproducible binary artifact for the exhaustive tic-tac-toe JEV model. */
public final class TicTacToeModelArtifact {
    private TicTacToeModelArtifact() {
    }

    /** Encodes the quantized model produced by the exhaustive dataset and minimax oracle. */
    public static byte[] encoded() {
        TicTacToeDataset dataset = TicTacToeDataset.exhaustive();
        TicTacToeModelFactory factory = new TicTacToeModelFactory(new TicTacToeOracle());
        return new JevModelCodec().write(factory.quantizedModel(dataset));
    }

    /** Writes the reproducible artifact used by the compiler module. */
    public static void main(String[] arguments) throws IOException {
        Files.write(Path.of(arguments[0]), encoded());
    }
}
