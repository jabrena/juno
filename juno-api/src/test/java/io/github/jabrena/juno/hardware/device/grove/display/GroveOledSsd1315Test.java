package io.github.jabrena.juno.hardware.device.grove.display;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.i2c.I2c;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Grove SSD1315 OLED")
class GroveOledSsd1315Test {

    @Test
    @DisplayName("Draw pixel updates the caller-owned framebuffer and its display byte")
    void shouldDrawOnePixel() {
        I2c i2c = mock(I2c.class);
        GroveOledSsd1315 display = new GroveOledSsd1315(i2c, new byte[GroveOledSsd1315.FRAMEBUFFER_SIZE]);

        display.drawPixel(0, 0, Display.WHITE);

        var order = inOrder(i2c);
        order.verify(i2c).writeRegister(0x3C, 0x00, 0xB0);
        order.verify(i2c).writeRegister(0x3C, 0x00, 0x00);
        order.verify(i2c).writeRegister(0x3C, 0x00, 0x10);
        order.verify(i2c).writeRegister(0x3C, 0x40, 1);
    }

    @Test
    @DisplayName("Requires an entire 128 by 64 framebuffer")
    void shouldRequireAFullFramebuffer() {
        I2c i2c = mock(I2c.class);

        assertThatThrownBy(() -> new GroveOledSsd1315(i2c, new byte[1023]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Initializes, clears, draws text and ignores pixels outside the display")
    void shouldInitializeAndDrawText() {
        I2c i2c = mock(I2c.class);
        GroveOledSsd1315 display = new GroveOledSsd1315(i2c, new byte[GroveOledSsd1315.FRAMEBUFFER_SIZE]);

        display.begin();
        display.fillScreen(Display.BLACK);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setTextSize(0);
        display.setCursor(0, 0);
        display.print('A');
        display.print("\nB");
        display.println("!");
        display.drawPixel(-1, 0, Display.WHITE);

        assertThat(display.width()).isEqualTo(128);
        assertThat(display.height()).isEqualTo(64);
        verify(i2c).begin();
    }
}
