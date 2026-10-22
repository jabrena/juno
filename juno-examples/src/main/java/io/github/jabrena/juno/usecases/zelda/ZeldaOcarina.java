package io.github.jabrena.juno.usecases.zelda;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxButtons;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

/**
 * Selects and plays the Ocarina of Time melodies on a Juno-powered RCX brick.
 *
 * <p>Press VIEW on the RCX to select the next melody. Its name scrolls across the four-character RCX display. Press
 * RUN to play the selected melody. Press VIEW or RUN while a melody is playing to cancel it and select the next
 * melody.
 *
 * <p>The IR transmitter's DAT wire is connected to pin 3 and the receiver's OUT wire to pin 2. The RCX must be
 * running Juno's {@code RcxRemoteFirmware}; LEGO's original firmware does not implement the LCD and button commands.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class ZeldaOcarina {
    private static final Clock CLOCK = Arduino.getClock();
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;
    private static final int POLL_MILLIS = 60;
    private static final int SCROLL_MILLIS = 500;
    private static final int MAX_TONE_SEGMENT_MILLIS = 2500;
    private static final int CANCEL_FREQUENCY_HZ = 31;

    private final RcxButtons buttons;
    private final RcxLcd lcd;
    private final RcxAudio audio;
    private final char[] displayFrame = new char[ZeldaOcarinaSongs.FRAME_WIDTH];
    private int selected;
    private int scrollFrame;
    private int nextScrollAt;

    ZeldaOcarina(RcxButtons buttons, RcxLcd lcd, RcxAudio audio) {
        this.buttons = buttons;
        this.lcd = lcd;
        this.audio = audio;
    }

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        Serial.println("Zelda Ocarina starting");

        RcxLcd lcd = remote.getLcd();
        Serial.println("Waiting for RCX firmware");
        while (!lcd.clear()) {
            Serial.println("RCX did not answer: check power, firmware, range and IR wiring");
            Delay.millis(RETRY_MILLIS);
        }

        RcxButtons buttons = remote.getButtons();
        buttons.clicked();
        RcxAudio audio = remote.getAudio();
        audio.playSound(RcxAudio.SOUND_FAST_SWEEP_UP);
        Delay.millis(500);
        ZeldaOcarina app = new ZeldaOcarina(buttons, lcd, audio);
        app.showSelection();
        Serial.println("VIEW selects; RUN plays; VIEW or RUN during playback cancels");
        app.loop();
    }

    private void loop() {
        while (true) {
            int clicked = buttons.clicked();
            if (clicked >= 0) {
                if ((clicked & RcxButtons.VIEW) != 0) {
                    selectNext();
                } else if ((clicked & RcxButtons.RUN) != 0) {
                    playSelected();
                }
            }
            scrollSelection();
            Delay.millis(POLL_MILLIS);
        }
    }

    private void selectNext() {
        selected = ZeldaOcarinaSongs.nextIndex(selected);
        showSelection();
        Serial.println(ZeldaOcarinaSongs.title(selected));
    }

    private void showSelection() {
        int song = selected;
        scrollFrame = 0;
        showFrame(song, scrollFrame);
        nextScrollAt = CLOCK.millis() + SCROLL_MILLIS;
    }

    private void scrollSelection() {
        if (CLOCK.millis() < nextScrollAt) {
            return;
        }
        scrollFrame = scrollFrame + 1;
        if (scrollFrame == ZeldaOcarinaSongs.frameCount(selected)) {
            scrollFrame = 0;
        }
        showFrame(selected, scrollFrame);
        nextScrollAt = CLOCK.millis() + SCROLL_MILLIS;
    }

    private void showFrame(int song, int frame) {
        ZeldaOcarinaSongs.split(song, frame, displayFrame);
        lcd.showText(displayFrame);
    }

    private void playSelected() {
        int song = selected;
        Serial.println(ZeldaOcarinaSongs.title(song));
        Serial.println("Playing");
        boolean cancelled = playSong(song);
        if (cancelled) {
            Serial.println("Cancelled");
            audio.playTone(CANCEL_FREQUENCY_HZ, 1);
            selectNext();
            return;
        }
        Serial.println("Finished");
        showSelection();
    }

    private boolean playSong(int song) {
        int noteStart = CLOCK.millis();
        for (int repeat = 0; repeat < ZeldaOcarinaSongs.repeatCount(song); repeat++) {
            for (int note = 0; note < ZeldaOcarinaSongs.noteCount(song); note++) {
                if (playTimedTone(
                        ZeldaOcarinaSongs.frequencyHz(song, note),
                        ZeldaOcarinaSongs.toneMillis(song, note),
                        ZeldaOcarinaSongs.stepMillis(song, note),
                        noteStart)) {
                    return true;
                }
                noteStart = noteStart + ZeldaOcarinaSongs.stepMillis(song, note);
            }
        }
        return false;
    }

    private boolean playTimedTone(int frequencyHz, int toneMillis, int stepMillis, int noteStart) {
        int toneEnd = noteStart + toneMillis;
        int segmentEnd = noteStart;
        while (segmentEnd < toneEnd) {
            int remainingTone = toneEnd - segmentEnd;
            int segmentMillis = remainingTone < MAX_TONE_SEGMENT_MILLIS
                    ? remainingTone
                    : MAX_TONE_SEGMENT_MILLIS;
            audio.playTone(frequencyHz, (segmentMillis + 9) / 10);
            segmentEnd = segmentEnd + segmentMillis;
            if (waitUntil(segmentEnd)) {
                return true;
            }
        }
        return waitUntil(noteStart + stepMillis);
    }

    private boolean waitUntil(int deadline) {
        while (CLOCK.millis() < deadline) {
            int remaining = deadline - CLOCK.millis();
            Delay.millis(remaining < POLL_MILLIS ? remaining : POLL_MILLIS);
            int clicked = buttons.clicked();
            if (clicked >= 0 && (clicked & (RcxButtons.VIEW | RcxButtons.RUN)) != 0) {
                return true;
            }
        }
        return false;
    }
}
