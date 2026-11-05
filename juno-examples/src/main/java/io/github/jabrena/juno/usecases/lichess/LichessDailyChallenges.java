package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.lichess.challenge.LichessModel;
import io.github.jabrena.juno.usecases.lichess.controller.LichessController;
import io.github.jabrena.juno.usecases.lichess.services.LichessApi;
import io.github.jabrena.juno.util.Delay;

/**
 * A Lichess-powered chess tactics cabinet for the UNO R4 WiFi. The cover leads to an MVC challenge menu; the daily
 * and fresh public puzzle endpoints supply a FEN position and UCI solution, and the challenge view invites the player
 * to find the moves on the TFT touch board. The board-side program owns fixed buffers and makes no allocations in its
 * frame loop. Put {@code application.properties}, containing {@code wifi.ssid} and {@code wifi.password}, in the TFT
 * shield's microSD root.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LichessDailyChallenges {
    private LichessDailyChallenges() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        Serial.println("Lichess daily challenges");
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);

        LichessModel model = new LichessModel();
        LichessApi api = new LichessApi();
        api.connect(tft.getSdCard());
        new LichessController(tft, model, api).run();
    }
}
