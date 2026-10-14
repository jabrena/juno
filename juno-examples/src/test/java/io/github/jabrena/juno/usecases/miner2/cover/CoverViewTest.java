package io.github.jabrena.juno.usecases.miner2.cover;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverViewTest {
    @Test
    void theLogoThenTheBoardEachShowForThreeSecondsBeforeTheTitle() {
        assertThat(CoverView.step(0)).isEqualTo(CoverView.LOGO);
        assertThat(CoverView.step(2999)).isEqualTo(CoverView.LOGO);
        assertThat(CoverView.step(3000)).isEqualTo(CoverView.BOARD);
        assertThat(CoverView.step(5999)).isEqualTo(CoverView.BOARD);
        assertThat(CoverView.step(6000)).isEqualTo(CoverView.TITLE);
        assertThat(CoverView.step(600_000)).isEqualTo(CoverView.TITLE);
    }

    @Test
    void thePlayButtonIsTheTouchTarget() {
        assertThat(CoverView.isPlay(50, 200)).isTrue();
        assertThat(CoverView.isPlay(189, 249)).isTrue();
        assertThat(CoverView.isPlay(120, 120)).as("the title").isFalse();
        assertThat(CoverView.isPlay(190, 225)).isFalse();
    }

    @Test
    void bothImagesFitThePortraitScreen() {
        assertThat(JavaLogoImage.WIDTH).isLessThanOrEqualTo(240);
        assertThat(JavaLogoImage.HEIGHT).isLessThanOrEqualTo(320);
        assertThat(BoardImage.WIDTH).as("the board is on its side").isLessThan(BoardImage.HEIGHT);
        assertThat(BoardImage.HEIGHT).isLessThanOrEqualTo(320);
    }
}
