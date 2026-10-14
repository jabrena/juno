extern "C" int z_impl_sys_csrand_get(void* target, uint32_t length);

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  return z_impl_sys_csrand_get(target, length) == 0;
}
