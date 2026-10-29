package io.github.jabrena.juno.usecases.zeldapoliphonicrcx;

import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannelAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.util.Delay;

/** Plays the Ocarina of Time opening as three synchronized instruments on RCX channels one, two and three. */
public final class ZeldaPolyphonicRcx {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int START_MILLIS = 15000;
    private static final int MELODY_CHANNEL = 1;
    private static final int HARMONY_CHANNEL = 2;
    private static final int COUNTERPOINT_CHANNEL = 3;
    static final int REFERENCE_FREQUENCY_HZ = 440;
    static final int REFERENCE_DURATION_CENTISECONDS = 50;
    static final int[] ORCHESTRA_CHANNELS = {MELODY_CHANNEL, HARMONY_CHANNEL, COUNTERPOINT_CHANNEL};
    /** The current physical wiring: each of the three bricks has its indicator lamp on output A. */
    private static final RcxLight MELODY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight HARMONY_LIGHT = new RcxLight(RcxLight.PORT_A);
    private static final RcxLight COUNTERPOINT_LIGHT = new RcxLight(RcxLight.PORT_A);

    private ZeldaPolyphonicRcx() {
    }

    public static void main(String[] args) {
        RcxLink link = new RcxLink();
        RcxChannelAudio audio = new RcxChannelAudio(link);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        link.begin(new HXM121InfraredReceiver(RECEIVE_PIN), new HX53InfraredEmitter(TRANSMIT_PIN));
        Delay.millis(START_MILLIS);
        Serial.println("Playing Zelda Ocarina on RCX channels 1, 2 and 3");
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
