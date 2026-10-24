package io.github.jabrena.juno.games.doom.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies level generation with a deterministic WAD fixture. A local DOOM WAD can also be generated on demand with
 * {@code ./mvnw -f juno-examples/pom.xml test -Dtest=LevelGeneratorTest -Djuno.doom.wad=/path/DOOM1.WAD}.
 * The real-WAD output is temporary unless {@code -Djuno.doom.output=...} is provided explicitly.
 */
class LevelGeneratorTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void generatesLevelTablesFromDeterministicFixture() throws IOException {
        Path wad = temporaryDirectory.resolve("fixture.WAD");
        Path output = temporaryDirectory.resolve("Level.java");
        writeFixtureWad(wad);

        LevelGenerator.generate(wad, "E1M1", output);

        assertThat(output).isRegularFile();
        assertThat(Files.readString(output)).contains(
                "static final String NAME = \"E1M1\";",
                "static final int START_X = 32;",
                "static final int START_Y = 48;",
                "static final int START_ANGLE = 90;",
                "static final int EXIT_X = 64;",
                "static final int EXIT_Y = 0;",
                "static final int VERTICES = 2;",
                "static final int LINES = 1;",
                "static final int SEGS = 1;",
                "static final int NODES = 0;",
                "static final int SUBSECTORS = 1;",
                "static final int SECTORS = 1;",
                "static final short[] SUBSECTOR_SECTOR = {\n        0,");
    }

    @Test
    @EnabledIfSystemProperty(named = "juno.doom.wad", matches = ".+")
    void generatesLevelTablesFromConfiguredWad() throws IOException {
        Path wad = Path.of(System.getProperty("juno.doom.wad"));
        Path output = Path.of(System.getProperty("juno.doom.output",
                temporaryDirectory.resolve("Level.java").toString()));

        assertThat(wad).isRegularFile();
        LevelGenerator.generate(wad, System.getProperty("juno.doom.map", "E1M1"), output);
        assertThat(output).isRegularFile().isNotEmptyFile();
    }

    private static void writeFixtureWad(Path output) throws IOException {
        byte[][] lumps = {
            new byte[0],
            shorts(0, 1, 0, 11, 0, 0, -1),
            shorts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            shorts(0, 0, 128, 0),
            shorts(0, 1, 0, 0, 0, 0),
            shorts(1, 0),
            new byte[0],
            shorts(32, 48, 90, 1, 4),
            shorts(0, 128, 0, 0, 0, 0, 0, 0, 0, 0, 160, 0, 0),
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
