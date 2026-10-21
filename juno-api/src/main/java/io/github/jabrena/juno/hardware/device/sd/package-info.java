/**
 * SPI SD-card access: {@link io.github.jabrena.juno.hardware.device.sd.SdCard}, taken from the shield that holds
 * the card's socket (such as the {@link io.github.jabrena.juno.hardware.device.sd.DataLoggerShield}), opens files
 * through an {@link java.io.InputStream} accepted by Juno's bounded intrinsic implementation of {@link
 * java.util.Properties#load(java.io.InputStream)}.
 */
@NullMarked
package io.github.jabrena.juno.hardware.device.sd;

import org.jspecify.annotations.NullMarked;
