package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.ModelStatus;

/** A {@link ModelStatus} as text for the serial log: the board's enums have ordinals, not names. */
public final class Tny2Status {

    private Tny2Status() {
    }

    public static String describe(ModelStatus status) {
        return switch (status) {
            case OK -> "OK";
            case INVALID_HANDLE -> "invalid PSRAM handle";
            case INVALID_MODEL -> "invalid model";
            case EXHAUSTED -> "PSRAM exhausted";
            case NOT_LOADED -> "no model loaded";
            case INVALID_ARGUMENT -> "invalid question";
            case UNREADABLE -> "model file missing or unreadable";
        };
    }
}
