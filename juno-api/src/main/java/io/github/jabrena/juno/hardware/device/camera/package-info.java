/**
 * Still-image camera: {@link io.github.jabrena.juno.hardware.device.camera.Camera} delivers each JPEG frame into a buffer
 * the program owns. The AI Thinker ESP32-CAM's OV2640 is the camera Juno drives, taken from
 * {@link io.github.jabrena.juno.hardware.MCU#getCamera()}.
 */
@NullMarked
package io.github.jabrena.juno.hardware.device.camera;

import org.jspecify.annotations.NullMarked;
