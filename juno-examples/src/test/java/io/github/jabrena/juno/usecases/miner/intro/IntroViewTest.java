package io.github.jabrena.juno.usecases.miner.intro;

import io.github.jabrena.juno.util.image.ArduinoUnoBoardImage;
import io.github.jabrena.juno.util.image.JavaLogoImage;
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
        assertThat(ArduinoUnoBoardImage.WIDTH).as("the board is on its side").isLessThan(ArduinoUnoBoardImage.HEIGHT);
        assertThat(ArduinoUnoBoardImage.HEIGHT).isLessThanOrEqualTo(320);
    }
}
