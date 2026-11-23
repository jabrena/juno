// Juno's board-side java.lang.System subset for fixed primitive word arrays.
extern "C" void juno_system_arraycopy_words(const uint32_t* source, int32_t sourcePosition,
        uint32_t* destination, int32_t destinationPosition, int32_t length) {
  if (length <= 0) {
    return;
  }
  memmove(destination + destinationPosition, source + sourcePosition,
          static_cast<size_t>(length) * sizeof(uint32_t));
}
