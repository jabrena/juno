// A volume is mounted once SdFat knows its FAT type; each 512-byte sector is half a KiB.
static int32_t juno_sd_kib(uint32_t clusters) {
  uint64_t kib = static_cast<uint64_t>(clusters) * juno_sd.sectorsPerCluster() / 2u;
  return kib > 0x7fffffffu ? 0x7fffffff : static_cast<int32_t>(kib);
}

extern "C" int32_t juno_sd_total_kib() {
  return juno_sd.fatType() == 0 ? -1 : juno_sd_kib(juno_sd.clusterCount());
}

// Counting free clusters reads the whole FAT, seconds on a large card: it runs on the I/O worker where there is one.
static void juno_sd_free_kib_job(void* result) {
  int32_t clusters = juno_sd.freeClusterCount();
  *static_cast<int32_t*>(result) = clusters < 0 ? -1 : juno_sd_kib(static_cast<uint32_t>(clusters));
}

extern "C" int32_t juno_sd_free_kib() {
  if (juno_sd.fatType() == 0) return -1;
  JunoIoGuard guard(JUNO_IO_STORAGE);
  int32_t result = -1;
  juno_io_offload(juno_sd_free_kib_job, &result);
  return result;
}

