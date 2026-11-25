package io.github.jabrena.juno.models;

import io.github.jabrena.juno.dm.JevModelCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.StringJoiner;

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

    /** Encodes the model feature lookup as board-mask/index pairs for flash-resident examples. */
    public static String indexTable() {
        TicTacToeDataset dataset = TicTacToeDataset.exhaustive();
        StringJoiner table = new StringJoiner("");
        for (int index = 0; index < dataset.size(); index++) {
            TicTacToePosition position = dataset.positions().get(index);
            table.add(String.format(Locale.ROOT, "%04x%04x", (position.xMask() << 9) | position.oMask(), index));
        }
        return table.toString();
    }

    /** Writes the reproducible artifact used by the compiler module. */
    public static void main(String[] arguments) throws IOException {
        if (arguments.length > 1 && arguments[1].equals("index")) {
            Files.writeString(Path.of(arguments[0]), indexTable());
        } else {
            Files.write(Path.of(arguments[0]), encoded());
        }
    }
}
