// The Portenta Hat Carrier's microSD slot is on the H7's SDMMC2 bus, which SdFat cannot drive itself. SdFat mounts
// the card's FAT volume through this block device instead, so every SD and RandomAccessFile helper is the one the SPI
// boards share. It needs SdFat built with USE_BLOCK_DEVICE_INTERFACE=1, which the Portenta build passes.
class JunoSdmmcDevice : public FsBlockDeviceInterface {
 public:
  bool begin() {
    if (!started_) started_ = card_.init() == 0;
    return started_;
  }

  bool isBusy() override { return false; }

  bool readSector(uint32_t sector, uint8_t* dst) override { return readSectors(sector, dst, 1); }

  bool readSectors(uint32_t sector, uint8_t* dst, size_t count) override {
    return card_.read(dst, static_cast<mbed::bd_addr_t>(sector) * 512u, static_cast<mbed::bd_size_t>(count) * 512u)
        == 0;
  }

  uint32_t sectorCount() override { return static_cast<uint32_t>(card_.size() / 512u); }

  bool syncDevice() override { return card_.sync() == 0; }

  bool writeSector(uint32_t sector, const uint8_t* src) override { return writeSectors(sector, src, 1); }

  bool writeSectors(uint32_t sector, const uint8_t* src, size_t count) override {
    return card_.program(src, static_cast<mbed::bd_addr_t>(sector) * 512u,
                         static_cast<mbed::bd_size_t>(count) * 512u) == 0;
  }

 private:
  SDMMCBlockDevice card_;
  bool started_ = false;
};

static JunoSdmmcDevice juno_sdmmc;
