package io.github.jabrena.juno.usecases.miner2.intro;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntroViewTest {
    @Test
    void theLogoThenTheBoardAreShownInThatOrder() {
        assertThat(IntroView.NONE).isLessThan(IntroView.LOGO);
        assertThat(IntroView.LOGO).isLessThan(IntroView.BOARD);
        assertThat(IntroView.STEP_MILLIS).isEqualTo(3000);
    }

    @Test
    void bothImagesFitThePortraitScreen() {
        assertThat(JavaLogoImage.WIDTH).isLessThanOrEqualTo(240);
        assertThat(JavaLogoImage.HEIGHT).isLessThanOrEqualTo(320);
        assertThat(ArduinoBoardImage.WIDTH).as("the board is on its side").isLessThan(ArduinoBoardImage.HEIGHT);
        assertThat(ArduinoBoardImage.HEIGHT).isLessThanOrEqualTo(320);
    }
}
