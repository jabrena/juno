package io.github.jabrena.juno.games.doom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates the level tables from a small deterministic WAD fixture. Override {@code juno.doom.wad} to use another
 * WAD and {@code juno.doom.output} to choose a different output path. The default output is temporary, so the test
 * never overwrites the game's {@code Level.java}.
 */
class LevelGeneratorTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void generatesTheLevelTables() throws Exception {
        String configuredWad = System.getProperty("juno.doom.wad");
        Path wad = configuredWad == null || configuredWad.isBlank()
                ? temporaryDirectory.resolve("fixture.WAD")
                : Path.of(configuredWad);
        if (configuredWad == null || configuredWad.isBlank()) {
            writeFixtureWad(wad);
        }
        Path output = Path.of(System.getProperty("juno.doom.output",
                temporaryDirectory.resolve("Level.java").toString()));

        assertThat(Files.exists(wad)).as("DOOM WAD: %s", wad).isTrue();
        LevelGenerator.generate(wad, System.getProperty("juno.doom.map", "E1M1"), output);
        assertThat(Files.size(output)).isPositive();
    }

    private static void writeFixtureWad(Path output) throws IOException {
        byte[][] lumps = {
            new byte[0],
            shorts(0, 0, 0, 11, 0, 0, -1),
            new byte[30],
            shorts(0, 0, 128, 0),
            shorts(1, 0),
            new byte[0],
            new byte[0],
            shorts(0, 0, 0, 1, 4),
            shorts(0, 0, 128, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        };
        String[] names = {
            "E1M1", "LINEDEFS", "SIDEDEFS", "VERTEXES", "SEGS", "SSECTORS", "NODES", "THINGS", "SECTORS"
        };
        int directoryOffset = 12;
        for (byte[] lump : lumps) {
            directoryOffset += lump.length;
        }
        ByteBuffer wad = ByteBuffer.allocate(directoryOffset + names.length * 16).order(ByteOrder.LITTLE_ENDIAN);
        wad.put("PWAD".getBytes(StandardCharsets.US_ASCII)).putInt(names.length).putInt(directoryOffset);
        int offset = 12;
        for (byte[] lump : lumps) {
            wad.position(offset).put(lump);
            offset += lump.length;
        }
        offset = 12;
        for (int index = 0; index < names.length; index++) {
            byte[] name = names[index].getBytes(StandardCharsets.US_ASCII);
            wad.position(directoryOffset + index * 16).putInt(offset).putInt(lumps[index].length).put(name);
            offset += lumps[index].length;
        }
        Files.write(output, wad.array());
    }

    private static byte[] shorts(int... values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            bytes.putShort((short) value);
        }
        return bytes.array();
    }
}
