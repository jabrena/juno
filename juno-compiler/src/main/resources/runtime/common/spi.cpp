extern "C" void juno_spi_begin() {
  SPI.begin();
}

// Clocks one byte out and returns the byte clocked in; the bus is full duplex.
extern "C" int32_t juno_spi_transfer(int32_t value) {
  return SPI.transfer(static_cast<uint8_t>(value));
}
