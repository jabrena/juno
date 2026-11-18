
#include "pinmap.h"
#include "PeripheralPins.h"
#include "stm32h7xx_ll_spi.h"

// The Mbed core moves an SPI buffer one byte per driver call, about 1 us a byte whatever the clock. On the H7 the
// core leaves its SPI peripheral running an endless full-duplex transfer, so a write-only buffer can instead be fed
// straight into the peripheral's FIFO, a few bytes ahead of the ones coming back, which are drained and dropped.
// Falls back to the core's transfer, a copy at a time, whenever the peripheral is not in that state.
static void juno_spi_write_chunked(const int8_t* data, int32_t length) {
  uint8_t chunk[64];
  int32_t offset = 0;
  while (offset < length) {
    int32_t count = length - offset < 64 ? length - offset : 64;
    memcpy(chunk, data + offset, static_cast<size_t>(count));
    SPI.transfer(chunk, static_cast<size_t>(count));
    offset += count;
  }
}

extern "C" void juno_spi_write_buffer(int8_t* data, int32_t length) {
  static SPI_TypeDef* spi = reinterpret_cast<SPI_TypeDef*>(
      pinmap_peripheral(digitalPinToPinName(PIN_SPI_MOSI), PinMap_SPI_MOSI));
  if (length <= 0) return;
  if (spi == nullptr || !LL_SPI_IsEnabled(spi) || !LL_SPI_IsActiveMasterTransfer(spi)) {
    juno_spi_write_chunked(data, length);
    return;
  }
  // Up to 8 bytes in flight: half the H7 SPI FIFO, so the receive side never overruns.
  int32_t sent = 0;
  int32_t received = 0;
  uint32_t idle = 0;
  while (received < length) {
    bool progressed = false;
    if (sent < length && sent - received < 8 && LL_SPI_IsActiveFlag_TXP(spi)) {
      LL_SPI_TransmitData8(spi, static_cast<uint8_t>(data[sent]));
      sent++;
      progressed = true;
    }
    if (LL_SPI_IsActiveFlag_RXP(spi)) {
      (void) LL_SPI_ReceiveData8(spi);
      received++;
      progressed = true;
    }
    if (progressed) {
      idle = 0;
    } else if (++idle > 1000000u) {
      return;  // The peripheral stopped answering; give up rather than hang the program.
    }
  }
}
