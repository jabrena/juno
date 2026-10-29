package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannelAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.util.Delay;

/**
 * Plays the Ocarina of Time opening from an Arduino Nano ESP32 through the Nano Connector Carrier.
 *
 * <p>The carrier wiring shown in the example setup is named instead of hard-coded: the infrared transmitter is in
 * the Grove I2C socket (signal SCL/A5) and the infrared receiver is in the Grove A0/A1 socket (signal A0). The
 * carrier supplies power and ground to both modules.
 *
 * <p>The three RCX bricks listen on channels one, two and three.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.usecases.zeldapoliphonicrcx.ZeldaPolyphonicRcxNano \
 *   -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class ZeldaPolyphonicRcxNano {
    private static final int START_MILLIS = 15000;
    private static final int MELODY_CHANNEL = 1;
    private static final int HARMONY_CHANNEL = 2;
    private static final int COUNTERPOINT_CHANNEL = 3;
    static final int REFERENCE_FREQUENCY_HZ = 440;
    static final int REFERENCE_DURATION_CENTISECONDS = 50;
    static final int[] ORCHESTRA_CHANNELS = {MELODY_CHANNEL, HARMONY_CHANNEL, COUNTERPOINT_CHANNEL};
    /** The current physical wiring: each RCX brick has its activity lamp on output A. */
    private static final RcxLight MELODY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight HARMONY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight COUNTERPOINT_LIGHT = new RcxLight(RcxLight.PORT_A);

    private ZeldaPolyphonicRcxNano() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getI2cScl());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA0());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(receiver, emitter);
        Delay.millis(START_MILLIS);
        Serial.println("Nano carrier IR: TX A5, RX A0");
        Serial.println("Playing Zelda Ocarina on Nano RCX channels 1, 2 and 3");
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
    }

    /** Plays the same A4 reference tone on every channel so the three brick volumes can be compared before the piece. */
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
                if (melody > 0) {
                    audio.playTone(MELODY_CHANNEL, melody, duration);
                }
                if (harmony > 0) {
                    audio.playTone(HARMONY_CHANNEL, harmony, duration);
                }
                if (counterpoint > 0) {
                    audio.playTone(COUNTERPOINT_CHANNEL, counterpoint, duration);
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
