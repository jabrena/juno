package io.github.jabrena.juno.usecases.poliphonic;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.util.Delay;

/**
 * Plays "Ode to Joy" from an Arduino Nano ESP32 through the Nano Connector Carrier, distributing four voices over
 * four RCX bricks.
 */
@Board({ArduinoNanoEsp32Board.class})
public class OdeToJoyPoliphonicRcxNano {
    private static final int START_MILLIS = 15000;
    private static final int MELODY_CHANNEL = 1;
    private static final int HARMONY_CHANNEL = 2;
    private static final int COUNTERPOINT_CHANNEL = 3;
    private static final int BASS_CHANNEL = 4;
    public static final int REFERENCE_FREQUENCY_HZ = 440;
    public static final int REFERENCE_DURATION_CENTISECONDS = 50;
    public static final int[] ORCHESTRA_CHANNELS = {MELODY_CHANNEL, HARMONY_CHANNEL, COUNTERPOINT_CHANNEL, BASS_CHANNEL};
    private static final RcxLight MELODY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight HARMONY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight COUNTERPOINT_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight BASS_LIGHT = new RcxLight(RcxLight.PORT_A);

    private OdeToJoyPoliphonicRcxNano() {
    }

    public static void main(String[] args) {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(ArduinoNano.getClock());
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(receiver, emitter);
        Delay.millis(START_MILLIS);
        Serial.println("Nano carrier IR: TX A0, RX A5");
        Serial.println("Playing Ode to Joy on Nano RCX channels 1, 2, 3 and 4");
        configureIndicators(audio);
        playReferenceTone(audio);
        play(audio);
        Serial.println("Ode to Joy finished");
    }

    private static void configureIndicators(RcxChannelAudio audio) {
        audio.configureIndicator(MELODY_CHANNEL, MELODY_LIGHT);
        audio.configureIndicator(HARMONY_CHANNEL, HARMONY_LIGHT);
        audio.configureIndicator(COUNTERPOINT_CHANNEL, COUNTERPOINT_LIGHT);
        audio.configureIndicator(BASS_CHANNEL, BASS_LIGHT);
    }

    private static void playReferenceTone(RcxChannelAudio audio) {
        for (int channel : ORCHESTRA_CHANNELS) {
            audio.playTone(channel, REFERENCE_FREQUENCY_HZ, REFERENCE_DURATION_CENTISECONDS);
        }
        Delay.millis(REFERENCE_DURATION_CENTISECONDS * 10 + 100);
    }

    private static void play(RcxChannelAudio audio) {
        for (int repeat = 0; repeat < OdeToJoyTheme.repeatCount(); repeat++) {
            for (int note = 0; note < OdeToJoyTheme.noteCount(); note++) {
                int toneMillis = OdeToJoyTheme.toneMillis(note);
                int duration = durationCentiseconds(toneMillis);
                int melody = OdeToJoyTheme.frequencyHz(note);
                int harmony = OdeToJoyHarmony.frequencyHz(note);
                int counterpoint = OdeToJoyCounterpoint.frequencyHz(note);
                int bass = OdeToJoyBass.frequencyHz(note);
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
                Delay.millis(OdeToJoyTheme.stepMillis(note));
            }
        }
    }

    public static int durationCentiseconds(int toneMillis) {
        int centiseconds = toneMillis / 10;
        if (centiseconds < 1) {
            return 1;
        }
        return centiseconds > 255 ? 255 : centiseconds;
    }
}
