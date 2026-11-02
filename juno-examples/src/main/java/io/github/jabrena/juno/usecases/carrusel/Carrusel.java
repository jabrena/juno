package io.github.jabrena.juno.usecases.carrusel;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.ViewRouter;

/** Wires the model-view-controller pieces for the Java-to-hardware image carousel. */
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class Carrusel {
    private static final int FRAME_PERIOD_MILLIS = 50;
    private static final int CAROUSEL_VIEW = 0;

    private Carrusel() {
    }

    public static void main() {
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(500);
        Serial.println("Java image carousel");

        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT);
        ViewRouter controller = new ViewRouter(tft, FRAME_PERIOD_MILLIS);
        controller.register(CAROUSEL_VIEW, new CarruselView(tft, ArduinoUno.getClock()));
        controller.start(CAROUSEL_VIEW);
        controller.run();
    }
}
