package io.github.jabrena.juno.hardware;

/**
 * Which board the program was compiled for. {@link MCU#getBoardModel()} answers with one constant, which the compiler
 * folds for the target board: a program asks the question without naming a board, so the same source runs on every
 * board and each one answers for itself.
 *
 * <p>Because the answer is a constant, a {@code switch} over it is decided when the program is linked, and the arms
 * the target can never take are dropped — their code and the shim support behind it included. That is what lets one
 * method pick a board's hardware without every board paying for all of it, as
 * {@link io.github.jabrena.juno.hardware.MCU#getSecretStore} does for the secret stores:
 *
 * <pre>{@code
 * return switch (MCU.getBoardModel()) {
 *     case PORTENTA_H7 -> new Se050C2();
 *     case NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM, UNO_R4_WIFI -> new Esp32Nvs();
 *     case PICO_2_W -> new Pico2WTrustZoneStore();
 *     default -> throw new IllegalStateException("This board has no secret store");
 * };
 * }</pre>
 *
 * <p>A constant is its ordinal on the board, so asking costs nothing, and a test uses the constants directly rather
 * than mocking anything. The order here is the order of the compiler's own board table, and a test keeps the two in
 * step.
 */
public enum BoardModel {
    /** Arduino UNO R3, with no radio of its own. */
    UNO_R3,
    /** Arduino UNO R4 WiFi: Renesas core, 12-column LED matrix. */
    UNO_R4_WIFI,
    /** Arduino UNO Q: the most memory, and the 13-column LED matrix. */
    UNO_Q,
    /** Arduino UNO WiFi Rev2: megaAVR core, with an onboard IMU. */
    UNO_R3_WIFI_REV2,
    /** Arduino Nano ESP32: an ESP32-S3 in a NORA-W106 module, with no secure element. */
    NANO_ESP32,
    /** AI Thinker ESP32-CAM: an ESP32 with PSRAM and the OV2640 camera. */
    AI_THINKER_ESP32_CAM,
    /** M5Stack Cardputer: an ESP32-S3 with a display, a keyboard and a speaker. */
    M5_CARDPUTER,
    /** Raspberry Pi Pico 2 W: an RP2350 with Wi-Fi. */
    PICO_2_W,
    /** Arduino Portenta H7: a Cortex-M7 with an onboard NXP SE050C2 secure element. */
    PORTENTA_H7
}
