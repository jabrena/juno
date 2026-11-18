/**
 * Mailbox, a message channel for Claude between an M5Stack Cardputer and an Arduino Portenta H7. The {@code server}
 * package keeps messages on the Portenta Hat Carrier's SD card behind an HTTPS API guarded by an API key; the {@code client} package runs on the
 * Cardputer and reads and writes them.
 */
package io.github.jabrena.juno.usecases.mailbox;
