package io.github.jabrena.juno.games.doom2.commons;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Doom2StateTest {
    @Test
    void theCoverPromptBlinksForThirteenFramesThenHidesForEight() {
        Doom2State state = new Doom2State();

        for (int frame = 0; frame < 42; frame++) {
            assertThat(state.coverPromptVisible()).as("frame %s", frame).isEqualTo(frame % 21 < 13);
            state.advanceCover();
        }
    }

    @Test
    void resettingTheCoverRestartsItsAnimation() {
        Doom2State state = new Doom2State();
        for (int frame = 0; frame < 15; frame++) {
            state.advanceCover();
        }
        assertThat(state.coverPromptVisible()).isFalse();

        state.resetCover();

        assertThat(state.coverPromptVisible()).isTrue();
    }

    @Test
    void thePilotCanSwitchBetweenCpuAndHuman() {
        Doom2State state = new Doom2State();
        assertThat(state.autopilot()).isTrue();

        state.chooseHuman();
        assertThat(state.autopilot()).isFalse();

        state.chooseCpu();
        assertThat(state.autopilot()).isTrue();
    }

    @Test
    void theCardStartupResultIsPublished() {
        Doom2State state = new Doom2State();
        assertThat(state.cardReady()).isFalse();

        state.publishCardReady(true);

        assertThat(state.cardReady()).isTrue();
    }

    @Test
    void onlyEpisodesDiscoveredInTheWadCanBeSelected() {
        Doom2State state = new Doom2State();
        assertThat(state.wadScanComplete()).isFalse();

        state.publishEpisodesInWad(0b1010);
        state.chooseEpisode(3);

        assertThat(state.wadScanComplete()).isTrue();
        assertThat(state.episodeAvailable(1)).isTrue();
        assertThat(state.episodeAvailable(2)).isFalse();
        assertThat(state.episodeAvailable(3)).isTrue();
        assertThat(state.episode()).isEqualTo(3);
        assertThat(state.map()).isEqualTo(1);

        state.chooseEpisode(2);
        assertThat(state.episode()).isEqualTo(3);
    }

    @Test
    void choosingAnEpisodeStartsANewGameAtItsFirstMapOnce() {
        Doom2State state = new Doom2State();
        state.publishEpisodesInWad(1 << 1);

        state.chooseEpisode(1);

        assertThat(state.map()).isEqualTo(1);
        assertThat(state.takeNewGame()).isTrue();
        assertThat(state.takeNewGame()).isFalse();
    }

    @Test
    void advancingStopsAtTheEpisodesLastMap() {
        Doom2State state = new Doom2State();
        state.publishEpisodesInWad(1 << 1);
        state.chooseEpisode(1);

        for (int step = 0; step < 10; step++) {
            state.advanceMap();
        }

        assertThat(state.map()).isEqualTo(Doom2State.LAST_MAP);
        assertThat(state.lastMap()).isTrue();
    }

    @Test
    void skillStartsAtHurtMePlentyAndAcceptsOnlyDoomsFiveLevels() {
        Doom2State state = new Doom2State();
        assertThat(state.skill()).isEqualTo(3);

        state.chooseSkill(5);
        state.chooseSkill(6);
        state.chooseSkill(0);

        assertThat(state.skill()).isEqualTo(5);
    }
}
