/**
 * LEGO Powered Up hubs over Bluetooth LE: {@link io.github.jabrena.juno.api.lego.PoweredUpHubRemote},
 * backed by the Arduino {@code ArduinoBLE} library, and the LEGO Mindstorms RCX infrared remote
 * protocol: {@link io.github.jabrena.juno.api.lego.RcxRemote} and, for its sensors,
 * {@link io.github.jabrena.juno.api.lego.RcxBrick}, and Scout brick commands in
 * {@link io.github.jabrena.juno.api.lego.ScoutRemote}, plus the Power Functions IR receiver in
 * {@link io.github.jabrena.juno.api.lego.PowerFunctionsRemote}; the three infrared classes are built on
 * {@link io.github.jabrena.juno.api.io.ir.Infrared}.
 *
 * <p>Works on the UNO R4 WiFi (its ESP32-S3 module provides the BLE radio) and the UNO Q (whose
 * Linux side lends its Bluetooth adapter over {@code Arduino_RouterBridge}), with the
 * {@code ArduinoBLE} library installed ({@code arduino-cli lib install ArduinoBLE}; 2.1.0 or newer
 * on the UNO Q).
 */
@NullMarked
package io.github.jabrena.juno.api.lego;

import org.jspecify.annotations.NullMarked;
