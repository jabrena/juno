package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.jspecify.annotations.Nullable;

/**
 * Owns the one card in the selected board's onboard microSD socket. The chip select comes from board data through
 * {@link NativeBoardModel#onboardSdChipSelect()}, so the card is created on first use, only after the compiler has
 * validated the capability.
 */
public final class NativeOnboardSdCard {
    private static @Nullable SdCard sdCard;

    private NativeOnboardSdCard() {
    }

    /** The card in the selected board's onboard socket: the same instance on every call. */
    public static SdCard sdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(NativeBoardModel.onboardSdChipSelect());
            sdCard = card;
        }
        return card;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.MCU#getSdCard()} hand out {@code replacement}: for a whole-program
     * emulator or a unit test, never on the board. {@code null} restores the board's card.
     */
    public static void setSdCard(@Nullable SdCard replacement) {
        sdCard = replacement;
    }
}
