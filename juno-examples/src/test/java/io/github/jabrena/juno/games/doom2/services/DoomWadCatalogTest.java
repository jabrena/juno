package io.github.jabrena.juno.games.doom2.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DoomWadCatalogTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversOnlyEpisodesWhoseFirstMapExists() throws IOException {
        Path wadPath = temporaryDirectory.resolve("DOOM1.WAD");
        try (RandomAccessFile wad = new RandomAccessFile(wadPath.toFile(), "rw")) {
            writeHeader(wad, 4, 12);
            writeMarker(wad, "E1M1");
            writeMarker(wad, "E1M2");
            writeMarker(wad, "E3M1");
            writeMarker(wad, "PLAYPAL");
        }

        try (RandomAccessFile wad = new RandomAccessFile(wadPath.toFile(), "r")) {
            assertThat(DoomWadCatalog.episodes(wad)).isEqualTo(0b1010);
        }
    }

    @Test
    void rejectsAFileWithoutAWadHeader() throws IOException {
        Path wadPath = temporaryDirectory.resolve("NOT-A-WAD");
        try (RandomAccessFile wad = new RandomAccessFile(wadPath.toFile(), "rw")) {
            wad.write(new byte[12]);
        }

        try (RandomAccessFile wad = new RandomAccessFile(wadPath.toFile(), "r")) {
            assertThat(DoomWadCatalog.episodes(wad)).isZero();
        }
    }

    private static void writeHeader(RandomAccessFile wad, int lumps, int directory) throws IOException {
        wad.write('I');
        wad.write('W');
        wad.write('A');
        wad.write('D');
        writeInt32(wad, lumps);
        writeInt32(wad, directory);
    }

    private static void writeMarker(RandomAccessFile wad, String name) throws IOException {
        writeInt32(wad, 0);
        writeInt32(wad, 0);
        for (int index = 0; index < 8; index++) {
            wad.write(index < name.length() ? name.charAt(index) : 0);
        }
    }

    private static void writeInt32(RandomAccessFile wad, int value) throws IOException {
        wad.write(value);
        wad.write(value >> 8);
        wad.write(value >> 16);
        wad.write(value >> 24);
    }
}
