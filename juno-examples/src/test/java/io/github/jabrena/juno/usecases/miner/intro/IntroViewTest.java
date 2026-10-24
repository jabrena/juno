package io.github.jabrena.juno.usecases.miner.intro;

import io.github.jabrena.juno.util.image.ArduinoOneBoardImage;
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
        assertThat(ArduinoOneBoardImage.WIDTH).as("the board is on its side").isLessThan(ArduinoOneBoardImage.HEIGHT);
        assertThat(ArduinoOneBoardImage.HEIGHT).isLessThanOrEqualTo(320);
    }
}
