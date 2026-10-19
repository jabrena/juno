/**
 * Board I/O, one subpackage per interface: {@code gpio} holds the pin-level digital/analog
 * operations, {@code i2c} the I2C register access and {@code serial} Serial. SD-card access lives
 * in {@code io.github.jabrena.juno.api.thirdparty.sd}. Networking and USB HID live in the sibling
 * {@code io.github.jabrena.juno.api.net} and {@code io.github.jabrena.juno.api.hardware.hid} packages.
 */
@NullMarked
package io.github.jabrena.juno.api.hardware.io;

import org.jspecify.annotations.NullMarked;
