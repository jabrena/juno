extern "C" void juno_spi_begin() {
  SPI.begin();
}

// Clocks one byte out and returns the byte clocked in; the bus is full duplex.
extern "C" int32_t juno_spi_transfer(int32_t value) {
  return SPI.transfer(static_cast<uint8_t>(value));
}

// The clock for the transfers that follow, mode 0, most significant bit first. The transaction is ended at once:
// some cores take a lock in beginTransaction and release it in endTransaction, and the settings outlast both.
extern "C" void juno_spi_set_clock(int32_t frequency) {
  SPI.beginTransaction(SPISettings(static_cast<uint32_t>(frequency), MSBFIRST, SPI_MODE0));
  SPI.endTransaction();
}

// Clocks out a whole buffer in one call; the core writes the bytes clocked in back into it.
extern "C" void juno_spi_transfer_buffer(int8_t* data, int32_t length) {
  if (length > 0) SPI.transfer(data, static_cast<size_t>(length));
}
