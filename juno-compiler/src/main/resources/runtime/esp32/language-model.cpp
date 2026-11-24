// The float kernels of the JLM1 language model (int8 rows, attention, RMS norm, residual add, ReLU, expf), the only
// native code of its engine: JlmLanguageModel in juno-api
// runs the transformer in Java and calls these for the loops over a model's tables and its attention cache, both in
// PSRAM buffers. A row is a little-endian float scale followed by signed bytes; rows lie stride bytes apart. Each
// kernel checks its rows against the buffer, and PsramModelMemory checks the Java arrays before the call.
#include <math.h>
#include <string.h>

// Whether rows rows of stride bytes from offset, each using bytes 4 + skip to 4 + skip + cols, fit the buffer.
static bool juno_lm_rows_fit(const JunoPsramSlot* slot, int32_t offset, int32_t stride, int32_t rows, int32_t skip,
                             int32_t cols) {
  if (offset < 0 || stride < 4 || rows < 0 || skip < 0 || cols < 0) return false;
  if (static_cast<int64_t>(skip) + cols + 4 > stride) return false;
  return static_cast<int64_t>(offset) + static_cast<int64_t>(stride) * rows <= static_cast<int64_t>(slot->capacity);
}

static float juno_lm_float_at(const uint8_t* bytes) {
  float value;
  memcpy(&value, bytes, sizeof(value));
  return value;
}

extern "C" int32_t juno_lm_read_floats(int32_t handle, int32_t offset, float* target, int32_t target_offset,
                                       int32_t count) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (target == nullptr || !juno_psram_range_fits(offset, count * 4, slot->capacity)) return -5;
  memcpy(target + target_offset, static_cast<uint8_t*>(slot->pointer) + offset, static_cast<size_t>(count) * 4u);
  return 0;
}

extern "C" int32_t juno_lm_multiply_rows(int32_t handle, int32_t offset, int32_t stride, int32_t rows, int32_t skip,
                                         int32_t cols, const float* x, int32_t x_offset, float* y) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (x == nullptr || y == nullptr || !juno_lm_rows_fit(slot, offset, stride, rows, skip, cols)) return -5;
  const uint8_t* row = static_cast<uint8_t*>(slot->pointer) + offset;
  const float* input = x + x_offset;
  for (int32_t r = 0; r < rows; r++, row += stride) {
    const int8_t* values = reinterpret_cast<const int8_t*>(row + 4 + skip);
    float sum = 0.0f;
    for (int32_t c = 0; c < cols; c++) sum += static_cast<float>(values[c]) * input[c];
    y[r] = sum * juno_lm_float_at(row);
  }
  return 0;
}

// The divisor arrives as the raw bits of a Java float, whatever the board's floating-point calling convention.
extern "C" int32_t juno_lm_accumulate_rows(int32_t handle, int32_t offset, int32_t stride, int32_t rows,
                                           int32_t skip, int32_t cols, const float* weights, int32_t divisor_bits,
                                           float* out, int32_t out_offset) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (weights == nullptr || out == nullptr || !juno_lm_rows_fit(slot, offset, stride, rows, skip, cols)) return -5;
  float divisor;
  memcpy(&divisor, &divisor_bits, sizeof(divisor));
  const uint8_t* row = static_cast<uint8_t*>(slot->pointer) + offset;
  float* output = out + out_offset;
  for (int32_t r = 0; r < rows; r++, row += stride) {
    if (weights[r] == 0.0f) continue;
    const int8_t* values = reinterpret_cast<const int8_t*>(row + 4 + skip);
    float scale = weights[r] * juno_lm_float_at(row) / divisor;
    for (int32_t c = 0; c < cols; c++) output[c] += scale * static_cast<float>(values[c]);
  }
  return 0;
}

extern "C" int32_t juno_lm_quantize_row(int32_t handle, int32_t offset, const float* x, int32_t cols) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (x == nullptr || !juno_lm_rows_fit(slot, offset, cols + 4, 1, 0, cols)) return -5;
  float largest = 0.0f;
  for (int32_t i = 0; i < cols; i++) largest = fmaxf(largest, fabsf(x[i]));
  if (largest < 1e-20f) largest = 1e-20f;
  float factor = 127.0f / largest;
  uint8_t* row = static_cast<uint8_t*>(slot->pointer) + offset;
  float scale = largest / 127.0f;
  memcpy(row, &scale, sizeof(scale));
  int8_t* values = reinterpret_cast<int8_t*>(row + 4);
  for (int32_t i = 0; i < cols; i++) {
    float value = x[i] * factor;
    int32_t level = static_cast<int32_t>(value < 0 ? value - 0.5f : value + 0.5f);
    values[i] = static_cast<int8_t>(level < -127 ? -127 : (level > 127 ? 127 : level));
  }
  return 0;
}

// The softmax exponentials in single precision: Java's Math.exp is double, which the ESP32-S3 computes in software. The
// shift and the cutoff arrive as the raw bits of Java floats, like the divisor of juno_lm_accumulate_rows.
extern "C" int32_t juno_lm_exponentials(float* values, int32_t count, int32_t shift_bits, int32_t cutoff_bits) {
  if (values == nullptr || count < 0) return -5;
  float shift, cutoff;
  memcpy(&shift, &shift_bits, sizeof(shift));
  memcpy(&cutoff, &cutoff_bits, sizeof(cutoff));
  for (int32_t i = 0; i < count; i++) values[i] = values[i] < cutoff ? 0.0f : expf(values[i] - shift);
  return 0;
}

// One attention head, the computation of JlmInference.attend: scores over the cached keys, a softmax with expf, and
// the weighted sum of the cached values into out[skip..skip + cols). The divisor arrives as the raw bits of a float.
extern "C" int32_t juno_lm_attend(int32_t handle, int32_t key_offset, int32_t value_offset, int32_t stride,
                                  int32_t rows, int32_t skip, int32_t cols, const float* query, float* scores,
                                  int32_t divisor_bits, float* out) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -4;
  if (query == nullptr || scores == nullptr || out == nullptr
      || !juno_lm_rows_fit(slot, key_offset, stride, rows, skip, cols)
      || !juno_lm_rows_fit(slot, value_offset, stride, rows, skip, cols)) return -5;
  float divisor;
  memcpy(&divisor, &divisor_bits, sizeof(divisor));
  const uint8_t* base = static_cast<uint8_t*>(slot->pointer);
  const float* q = query + skip;
  float largest = -1e30f;
  const uint8_t* row = base + key_offset;
  for (int32_t j = 0; j < rows; j++, row += stride) {
    const int8_t* key = reinterpret_cast<const int8_t*>(row + 4 + skip);
    float sum = 0.0f;
    for (int32_t i = 0; i < cols; i++) sum += static_cast<float>(key[i]) * q[i];
    scores[j] = sum * juno_lm_float_at(row) / divisor;
    if (scores[j] > largest) largest = scores[j];
  }
  float total = 0.0f;
  for (int32_t j = 0; j < rows; j++) {
    scores[j] = scores[j] < -60.0f ? 0.0f : expf(scores[j] - largest);
    total += scores[j];
  }
  float* output = out + skip;
  for (int32_t i = 0; i < cols; i++) output[i] = 0.0f;
  row = base + value_offset;
  for (int32_t j = 0; j < rows; j++, row += stride) {
    if (scores[j] == 0.0f) continue;
    const int8_t* value = reinterpret_cast<const int8_t*>(row + 4 + skip);
    float scale = scores[j] * juno_lm_float_at(row) / total;
    for (int32_t i = 0; i < cols; i++) output[i] += scale * static_cast<float>(value[i]);
  }
  return 0;
}

// RMS normalization with gains, the computation of JlmInference.normalize (the square root in double, as Java does).
extern "C" int32_t juno_lm_rms_norm(const float* x, const float* gains, int32_t gain_offset, float* y, int32_t n) {
  if (x == nullptr || gains == nullptr || y == nullptr || n <= 0) return -5;
  float sum = 0.0f;
  for (int32_t i = 0; i < n; i++) sum += x[i] * x[i];
  float scale = static_cast<float>(1.0 / sqrt(static_cast<double>(sum / static_cast<float>(n) + 1e-5f)));
  const float* gain = gains + gain_offset;
  for (int32_t i = 0; i < n; i++) y[i] = x[i] * scale * gain[i];
  return 0;
}

extern "C" int32_t juno_lm_add(float* target, const float* source, int32_t n) {
  if (target == nullptr || source == nullptr || n < 0) return -5;
  for (int32_t i = 0; i < n; i++) target[i] += source[i];
  return 0;
}

extern "C" int32_t juno_lm_relu(float* x, int32_t n) {
  if (x == nullptr || n < 0) return -5;
  for (int32_t i = 0; i < n; i++) {
    if (x[i] < 0.0f) x[i] = 0.0f;
  }
  return 0;
}
