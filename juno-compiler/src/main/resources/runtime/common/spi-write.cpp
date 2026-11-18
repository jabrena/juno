
// Clocks out a whole buffer and leaves it unchanged. The core's block transfer writes the bytes clocked in back into
// what it sends, so it is handed a copy, a chunk at a time.
extern "C" void juno_spi_write_buffer(int8_t* data, int32_t length) {
  uint8_t chunk[64];
  int32_t offset = 0;
  while (offset < length) {
    int32_t count = length - offset < 64 ? length - offset : 64;
    memcpy(chunk, data + offset, static_cast<size_t>(count));
    SPI.transfer(chunk, static_cast<size_t>(count));
    offset += count;
  }
}
