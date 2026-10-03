/**
 * LEGO Powered Up hubs over Bluetooth LE: {@link io.github.jabrena.juno.api.lego.PoweredUpHub},
 * backed by the Arduino {@code ArduinoBLE} library.
 *
 * <p>Works on the UNO R4 WiFi (its ESP32-S3 module provides the BLE radio) and the UNO Q (whose
 * Linux side lends its Bluetooth adapter over {@code Arduino_RouterBridge}), with the
 * {@code ArduinoBLE} library installed ({@code arduino-cli lib install ArduinoBLE}; 2.1.0 or newer
 * on the UNO Q).
 */
package io.github.jabrena.juno.api.lego;
