package io.github.jabrena.juno.api.lcd.pomodoro;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * A Pomodoro timer on the LCD Keypad Shield. This class owns the state machine and the one piece
 * of model state that flows between screens (the chosen session length); each screen's own
 * rendering and input handling lives in its own view class: {@link ConfigurePomodoroView} (pick a
 * length with UP/DOWN, confirm with SELECT), {@link RunPomodoro} (the live {@code MM:SS}
 * countdown), and {@link TimeUpView} (the alarm, blinking until any button is pressed). The loop
 * then returns to {@link ConfigurePomodoroView} so another session can begin without rebooting the
 * board.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Pomodoro {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        LcdKeypadShield.begin();

        while (true) {
            int selectedMinutes = ConfigurePomodoroView.run();
            RunPomodoro.run(selectedMinutes);
            TimeUpView.run();
        }
    }
}
