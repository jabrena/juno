package io.github.jabrena.juno.usecases.poliphonic;

import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.util.Delay;

/** Plays "Ode to Joy" as three synchronized instruments on RCX channels one, two and three. */
public class OdeToJoyPoliphonicRcx {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int START_MILLIS = 15000;
    private static final int MELODY_CHANNEL = 1;
    private static final int HARMONY_CHANNEL = 2;
    private static final int COUNTERPOINT_CHANNEL = 3;
    public static final int REFERENCE_FREQUENCY_HZ = 440;
    public static final int REFERENCE_DURATION_CENTISECONDS = 50;
    public static final int[] ORCHESTRA_CHANNELS = {MELODY_CHANNEL, HARMONY_CHANNEL, COUNTERPOINT_CHANNEL};
    private static final RcxLight MELODY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight HARMONY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight COUNTERPOINT_LIGHT = new RcxLight(RcxLight.PORT_A);

    private OdeToJoyPoliphonicRcx() {
    }

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(new HXM121InfraredReceiver(RECEIVE_PIN), new HX53InfraredEmitter(TRANSMIT_PIN));
        Delay.millis(START_MILLIS);
        Serial.println("Playing Ode to Joy on RCX channels 1, 2 and 3");
        configureIndicators(audio);
        playReferenceTone(audio);
        play(audio);
        Serial.println("Ode to Joy finished");
    }

    private static void configureIndicators(RcxChannelAudio audio) {
        audio.configureIndicator(MELODY_CHANNEL, MELODY_LIGHT);
        audio.configureIndicator(HARMONY_CHANNEL, HARMONY_LIGHT);
        audio.configureIndicator(COUNTERPOINT_CHANNEL, COUNTERPOINT_LIGHT);
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
                if (melody > 0) {
                    audio.playTone(MELODY_CHANNEL, melody, duration);
                }
                if (harmony > 0) {
                    audio.playTone(HARMONY_CHANNEL, harmony, duration);
                }
                if (counterpoint > 0) {
                    audio.playTone(COUNTERPOINT_CHANNEL, counterpoint, duration);
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
