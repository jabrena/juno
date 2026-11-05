package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.util.Delay;

/**
 * Plays the Ocarina of Time opening from an Arduino UNO R4 WiFi through a Grove Base Shield.
 *
 * <p>The shield wiring is named instead of hard-coded: the Grove infrared emitter is in the D3 socket and the Grove
 * infrared receiver is in the D4 socket. The shield supplies power and ground to both modules.
 *
 * <p>The four RCX bricks listen on channels one, two, three and four.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.usecases.zeldapoliphonicrcx.ZeldaPolyphonicRcxR4 \
 *   -Djuno.board=arduino-uno-r4-wifi
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoUnoR4WiFiBoard.class})
public final class ZeldaPolyphonicRcxR4 {
    private static final int START_MILLIS = 15000;
    private static final int MELODY_CHANNEL = 1;
    private static final int HARMONY_CHANNEL = 2;
    private static final int COUNTERPOINT_CHANNEL = 3;
    private static final int BASS_CHANNEL = 4;
    static final int REFERENCE_FREQUENCY_HZ = 440;
    static final int REFERENCE_DURATION_CENTISECONDS = 50;
    static final int[] ORCHESTRA_CHANNELS = {MELODY_CHANNEL, HARMONY_CHANNEL, COUNTERPOINT_CHANNEL, BASS_CHANNEL};
    /** The current physical wiring: each RCX brick has its activity lamp on output A. */
    private static final RcxLight MELODY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight HARMONY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight COUNTERPOINT_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight BASS_LIGHT = new RcxLight(RcxLight.PORT_A);

    private ZeldaPolyphonicRcxR4() {
    }

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(shield.getD3());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(shield.getD4());
        RcxLink link = new RcxLink(ArduinoUno.getClock());
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(receiver, emitter);
        Delay.millis(START_MILLIS);
        Serial.println("Grove shield IR: TX D3, RX D4");
        Serial.println("Playing Zelda on RCX 1, 2, 3, 4");
        configureIndicators(audio);
        playReferenceTone(audio);
        play(audio);
        Serial.println("Zelda Ocarina finished");
    }

    /** Assigns each channel's activity lamp explicitly; moving a lamp to B or C changes only its RcxLight. */
    private static void configureIndicators(RcxChannelAudio audio) {
        audio.configureIndicator(MELODY_CHANNEL, MELODY_LIGHT);
        audio.configureIndicator(HARMONY_CHANNEL, HARMONY_LIGHT);
        audio.configureIndicator(COUNTERPOINT_CHANNEL, COUNTERPOINT_LIGHT);
        audio.configureIndicator(BASS_CHANNEL, BASS_LIGHT);
    }

    /** Plays the same A4 reference tone on every channel so the four brick volumes can be compared before the piece. */
    private static void playReferenceTone(RcxChannelAudio audio) {
        for (int channel : ORCHESTRA_CHANNELS) {
            audio.playTone(channel, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        }
        Delay.millis(REFERENCE_DURATION_CENTISECONDS * 10 + 100);
    }

    private static void play(RcxChannelAudio audio) {
        for (int repeat = 0; repeat < ZeldaOcarinaTheme.repeatCount(); repeat++) {
            for (int note = 0; note < ZeldaOcarinaTheme.noteCount(); note++) {
                int toneMillis = ZeldaOcarinaTheme.toneMillis(note);
                int duration = durationCentiseconds(toneMillis);
                int melody = ZeldaOcarinaTheme.frequencyHz(note);
                int harmony = ZeldaOcarinaHarmony.frequencyHz(note);
                int counterpoint = ZeldaOcarinaCounterpoint.frequencyHz(note);
                int bass = ZeldaOcarinaBass.frequencyHz(note);
                if (melody > 0) {
                    audio.playTone(MELODY_CHANNEL, melody, duration);
                }
                if (harmony > 0) {
                    audio.playTone(HARMONY_CHANNEL, harmony, duration);
                }
                if (counterpoint > 0) {
                    audio.playTone(COUNTERPOINT_CHANNEL, counterpoint, duration);
                }
                if (bass > 0) {
                    audio.playTone(BASS_CHANNEL, bass, duration);
                }
                Delay.millis(ZeldaOcarinaTheme.stepMillis(note));
            }
        }
    }

    static int durationCentiseconds(int toneMillis) {
        int centiseconds = toneMillis / 10;
        if (centiseconds < 1) {
            return 1;
        }
        return centiseconds > 255 ? 255 : centiseconds;
    }
}
