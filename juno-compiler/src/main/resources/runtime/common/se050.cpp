static bool juno_se050_started = false;

extern "C" int32_t juno_se050_begin() {
  if (!juno_se050_started) {
    juno_se050_started = SE05X.begin() != 0;
  }
  return juno_se050_started ? 1 : 0;
}

extern "C" int32_t juno_se050_read(int32_t objectId, int8_t* buffer, int32_t capacity) {
  if (!juno_se050_started || capacity < 0) {
    return -1;
  }
  size_t length = static_cast<size_t>(capacity);
  if (!SE05X.readBinaryObject(objectId, reinterpret_cast<uint8_t*>(buffer), length, &length)) {
    return -1;
  }
  return length <= static_cast<size_t>(capacity) ? static_cast<int32_t>(length) : -1;
}

extern "C" int32_t juno_se050_write(int32_t objectId, int8_t* value, int32_t length) {
  if (!juno_se050_started || length <= 0) {
    return 0;
  }
  if (SE05X.existsBinaryObject(objectId) && !SE05X.deleteBinaryObject(objectId)) {
    return 0;
  }
  return SE05X.writeBinaryObject(objectId, reinterpret_cast<const uint8_t*>(value), static_cast<size_t>(length));
}

extern "C" int32_t juno_se050_clear(int32_t objectId) {
  if (!juno_se050_started) {
    return 0;
  }
  return !SE05X.existsBinaryObject(objectId) || SE05X.deleteBinaryObject(objectId);
}
