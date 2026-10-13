// A volume is mounted once SdFat knows its FAT type; each 512-byte sector is half a KiB.
static int32_t juno_sd_kib(uint32_t clusters) {
  uint64_t kib = static_cast<uint64_t>(clusters) * juno_sd.sectorsPerCluster() / 2u;
  return kib > 0x7fffffffu ? 0x7fffffff : static_cast<int32_t>(kib);
}

extern "C" int32_t juno_sd_total_kib() {
  return juno_sd.fatType() == 0 ? -1 : juno_sd_kib(juno_sd.clusterCount());
}

// Without a trustworthy FSInfo count, the free clusters are counted from the whole FAT: a minute or more on a large
// card. SdFat's freeClusterCount() reads it in one busy loop, which on the UNO Q's I/O worker (a Zephyr thread above
// the Juno threads) would freeze every other thread until it finished. This counts the same entries a few sectors at
// a time and waits between slices, so the display keeps running: on the worker the wait is a Zephyr sleep, on a
// Juno thread a thread sleep.
static constexpr uint32_t JUNO_SD_SCAN_SLICE_SECTORS = 4u;
static constexpr uint32_t JUNO_SD_SCAN_PAUSE_MS = 10u;
static uint32_t juno_sd_scan_sector[128];

static uint32_t juno_sd_le16(const uint8_t* bytes) {
  return static_cast<uint32_t>(bytes[0]) | static_cast<uint32_t>(bytes[1]) << 8;
}

static uint32_t juno_sd_le32(const uint8_t* bytes) {
  return juno_sd_le16(bytes) | juno_sd_le16(bytes + 2) << 16;
}

// FAT32 keeps a free-cluster count in its FSInfo sector, kept up to date by the computer that last wrote the card:
// three sector reads instead of the whole FAT. Returns -1 when the volume has none worth trusting.
static int32_t juno_sd_fsinfo_free_clusters() {
  if (juno_sd.fatType() != 32) return -1;
  uint8_t* sector = reinterpret_cast<uint8_t*>(juno_sd_scan_sector);
  if (!juno_sd.card()->readSector(0, sector)) return -1;
  // Sector 0 is either the volume's own boot sector or an MBR whose first partition holds it.
  uint32_t volumeStart = memcmp(sector + 82, "FAT32   ", 8) == 0 ? 0u : juno_sd_le32(sector + 0x1C6);
  if (volumeStart != 0u && !juno_sd.card()->readSector(volumeStart, sector)) return -1;
  // The boot sector found must be the one SdFat mounted: its reserved sectors end where SdFat's FAT starts.
  if (volumeStart + juno_sd_le16(sector + 14) != juno_sd.fatStartSector()) return -1;
  uint32_t fsInfo = juno_sd_le16(sector + 48);
  if (fsInfo == 0u || fsInfo == 0xFFFFu || !juno_sd.card()->readSector(volumeStart + fsInfo, sector)) return -1;
  if (juno_sd_le32(sector) != 0x41615252u || juno_sd_le32(sector + 484) != 0x61417272u) return -1;
  uint32_t free = juno_sd_le32(sector + 488);
  return free > juno_sd.clusterCount() ? -1 : static_cast<int32_t>(free);
}

static void juno_sd_free_kib_job(void* result) {
  int32_t* kib = static_cast<int32_t*>(result);
  int32_t hinted = juno_sd_fsinfo_free_clusters();
  if (hinted >= 0) {
    *kib = juno_sd_kib(static_cast<uint32_t>(hinted));
    return;
  }
  uint8_t fatType = juno_sd.fatType();
  if (fatType != 16 && fatType != 32) return;
  uint32_t entriesPerSector = fatType == 16 ? 256u : 128u;
  // Entries 0 and 1 are reserved and never zero, so counting from entry 0 matches SdFat's own count.
  uint32_t todo = juno_sd.clusterCount() + 2u;
  uint32_t sector = juno_sd.fatStartSector();
  uint32_t free = 0;
  uint32_t sectorsRead = 0;
  while (todo > 0) {
    if (!juno_sd.card()->readSector(sector++, reinterpret_cast<uint8_t*>(juno_sd_scan_sector))) return;
    uint32_t entries = todo < entriesPerSector ? todo : entriesPerSector;
    if (fatType == 16) {
      const uint16_t* fat16 = reinterpret_cast<const uint16_t*>(juno_sd_scan_sector);
      for (uint32_t i = 0; i < entries; i++) {
        if (fat16[i] == 0) free++;
      }
    } else {
      for (uint32_t i = 0; i < entries; i++) {
        if (juno_sd_scan_sector[i] == 0) free++;
      }
    }
    todo -= entries;
    if (++sectorsRead % JUNO_SD_SCAN_SLICE_SECTORS == 0) juno_io_wait(JUNO_SD_SCAN_PAUSE_MS);
  }
  *kib = juno_sd_kib(free);
}

extern "C" int32_t juno_sd_free_kib() {
  if (juno_sd.fatType() == 0) return -1;
  JunoIoGuard guard(JUNO_IO_STORAGE);
  int32_t result = -1;
  juno_io_offload(juno_sd_free_kib_job, &result);
  return result;
}

