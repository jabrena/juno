package io.github.jabrena.juno.games.doom.map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoomMapLoaderTest {
    @Test
    void requestingAMapShowsItsProgressBarWithoutReadingIt() {
        FakeReader reader = new FakeReader(true);
        DoomMapLoader loader = new DoomMapLoader(reader);

        loader.begin(2, 3, 4, 40, 134, 200);

        assertThat(loader.status()).isEqualTo(DoomMapLoader.LOADING);
        assertThat(reader.barWidth).isEqualTo(200);
        assertThat(reader.loads).isZero();
    }

    @Test
    void steppingReadsTheRequestedMapOnce() {
        FakeReader reader = new FakeReader(true);
        DoomMapLoader loader = new DoomMapLoader(reader);
        loader.begin(2, 3, 4, 40, 134, 200);

        loader.step();
        loader.step();

        assertThat(reader.episode).isEqualTo(2);
        assertThat(reader.map).isEqualTo(3);
        assertThat(reader.skill).isEqualTo(4);
        assertThat(reader.loads).isEqualTo(1);
        assertThat(loader.status()).isEqualTo(DoomMapLoader.READY);
    }

    @Test
    void aMapTheReaderCannotLoadFails() {
        DoomMapLoader loader = new DoomMapLoader(new FakeReader(false));
        loader.begin(3, 1, 3, 40, 134, 200);

        loader.step();

        assertThat(loader.status()).isEqualTo(DoomMapLoader.FAILED);
    }

    @Test
    void aCancelledRequestIsNeverRead() {
        FakeReader reader = new FakeReader(true);
        DoomMapLoader loader = new DoomMapLoader(reader);
        loader.begin(1, 1, 3, 40, 134, 200);

        loader.cancel();
        loader.step();

        assertThat(loader.status()).isEqualTo(DoomMapLoader.IDLE);
        assertThat(reader.loads).isZero();
    }

    private static final class FakeReader implements MapReader {
        private final boolean succeeds;
        int episode;
        int map;
        int skill;
        int loads;
        int barWidth;

        FakeReader(boolean succeeds) {
            this.succeeds = succeeds;
        }

        @Override
        public void showProgress(int x, int y, int width) {
            barWidth = width;
        }

        @Override
        public boolean load(int episode, int map, int skill) {
            this.episode = episode;
            this.map = map;
            this.skill = skill;
            loads = loads + 1;
            return succeeds;
        }
    }
}
