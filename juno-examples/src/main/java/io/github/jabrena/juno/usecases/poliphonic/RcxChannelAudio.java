package io.github.jabrena.juno.usecases.poliphonic;

import io.github.jabrena.juno.remote.lego.rcx.RcxLight;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;


/** A zero-allocation channel-aware speaker for Juno's channelized RCX firmware. */
public final class RcxChannelAudio {
    private final RcxLink link;

    /** Creates channel audio sharing the supplied infrared link. */
    public RcxChannelAudio(RcxLink link) {
        this.link = link;
    }

    /** Plays a tone only on the brick selected for {@code channel}, numbered {@code 1..5}. */
    public void playTone(int channel, int frequencyHz, int durationCentiseconds) {
        link.sendChannelTone(channel, frequencyHz, durationCentiseconds);
    }

    /** Selects the motor output used as the tone indicator on the brick assigned to {@code channel}. */
    public void configureIndicator(int channel, RcxLight light) {
        if (channel < 1 || channel > 5) {
            throw new IllegalArgumentException("RCX channel must be between 1 and 5");
        }
        int indicatorOutput = light.port();
        if (indicatorOutput < 0 || indicatorOutput > 2) {
            throw new IllegalArgumentException("RCX indicator output must be A (0), B (1) or C (2)");
        }
        link.send(RcxLink.getChannelIndicatorOpcode(), channel, indicatorOutput, 2);
    }
}
