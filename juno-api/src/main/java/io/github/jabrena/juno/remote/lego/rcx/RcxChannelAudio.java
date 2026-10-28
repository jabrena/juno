package io.github.jabrena.juno.remote.lego.rcx;

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
        link.configureChannelIndicator(channel, light.port());
    }
}
