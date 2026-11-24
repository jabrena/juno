// TNY2 language-model engine (TINY 2.1, MIT): a character-level transformer whose weights are signed bytes with one
// float scale per row. juno_language_model_load copies a TNY2 file out of a PSRAM handle into one PSRAM block that
// also holds the attention cache; asking feeds the question one character per position, then nextChar picks and
// feeds one answer character per call, so the program streams the answer wherever it wants.
#include <math.h>
#include <string.h>

static constexpr int32_t JUNO_LM_VOCAB = 97;
static constexpr int32_t JUNO_LM_CONTEXT = 128;
static constexpr int32_t JUNO_LM_MAX_WIDTH = 128;
static constexpr int32_t JUNO_LM_MAX_HIDDEN = 512;
static constexpr int32_t JUNO_LM_MAX_LAYERS = 8;
static constexpr int32_t JUNO_LM_MAX_QUESTION = 72;
static constexpr int32_t JUNO_LM_END = 1;
static constexpr int32_t JUNO_LM_TOKEN_OFFSET = 30;
// The same rule as Tny2Inference: if any of the first LOOKAHEAD picks, counting a closing END, is less certain than the
// floor, the answer is "I do not know." instead of a guess. ask() makes those picks before nextChar streams anything.
static constexpr int32_t JUNO_LM_LOOKAHEAD = 8;
static constexpr float JUNO_LM_CONFIDENCE_FLOOR = 0.5f;
static const char JUNO_LM_UNKNOWN[] = "I do not know.";

struct JunoLmTable {
  const float* scale;
  const int8_t* weights;
  int32_t rows;
  int32_t cols;
};

struct JunoLmLayer {
  const float* attention_gain;
  JunoLmTable query, key, value, output;
  const float* feed_forward_gain;
  JunoLmTable up, down;
  int8_t* key_cache;
  int8_t* value_cache;
  float* key_scales;
  float* value_scales;
};

struct JunoLmReader {
  const uint8_t* data;
  uint32_t size;
  uint32_t position;
};

static uint8_t* juno_lm_block = nullptr;
static uint32_t juno_lm_block_used = 0;
static int32_t juno_lm_width = 0, juno_lm_layer_count = 0, juno_lm_heads = 0, juno_lm_head_width = 0;
static int32_t juno_lm_hidden = 0;
static float juno_lm_attention_divisor = 1.0f;
static JunoLmTable juno_lm_embedding, juno_lm_position_table;
static JunoLmLayer juno_lm_layers[JUNO_LM_MAX_LAYERS];
static const float* juno_lm_final_gain = nullptr;
static int32_t juno_lm_answer_position = 0;
static int32_t juno_lm_creativity = 0;
static bool juno_lm_answering = false;
static float juno_lm_confidence = 1.0f;
static char juno_lm_pending[JUNO_LM_LOOKAHEAD];
static int32_t juno_lm_pending_length = 0;
static int32_t juno_lm_pending_next = 0;
static bool juno_lm_pending_ended = false;
static const char* juno_lm_fallback = nullptr;
static float juno_lm_state[JUNO_LM_MAX_WIDTH], juno_lm_normalized[JUNO_LM_MAX_WIDTH], juno_lm_query[JUNO_LM_MAX_WIDTH];
static float juno_lm_temporary[JUNO_LM_MAX_WIDTH], juno_lm_attended[JUNO_LM_MAX_WIDTH];
static float juno_lm_hidden_values[JUNO_LM_MAX_HIDDEN], juno_lm_weights[JUNO_LM_CONTEXT];
static float juno_lm_scores[JUNO_LM_VOCAB];

static uint32_t juno_lm_align(uint32_t size) { return (size + 3u) & ~3u; }

static uint32_t juno_lm_table_bytes(int32_t rows, int32_t cols) {
  return juno_lm_align(static_cast<uint32_t>(rows) * 4u) + juno_lm_align(static_cast<uint32_t>(rows * cols));
}

static void* juno_lm_take(uint32_t size) {
  void* pointer = juno_lm_block + juno_lm_block_used;
  juno_lm_block_used += juno_lm_align(size);
  return pointer;
}

static bool juno_lm_read(JunoLmReader& reader, void* target, uint32_t size) {
  if (size > reader.size - reader.position) return false;
  memcpy(target, reader.data + reader.position, size);
  reader.position += size;
  return true;
}

static int32_t juno_lm_read16(JunoLmReader& reader) {
  uint8_t bytes[2];
  if (!juno_lm_read(reader, bytes, 2)) return -1;
  return bytes[0] | (bytes[1] << 8);
}

static bool juno_lm_read_table(JunoLmReader& reader, JunoLmTable& table, int32_t rows, int32_t cols) {
  float* scale = static_cast<float*>(juno_lm_take(static_cast<uint32_t>(rows) * 4u));
  int8_t* weights = static_cast<int8_t*>(juno_lm_take(static_cast<uint32_t>(rows * cols)));
  for (int32_t r = 0; r < rows; r++) {
    if (!juno_lm_read(reader, &scale[r], 4) || !juno_lm_read(reader, weights + r * cols, static_cast<uint32_t>(cols))) {
      return false;
    }
  }
  table = {scale, weights, rows, cols};
  return true;
}

static const float* juno_lm_read_gains(JunoLmReader& reader) {
  float* gains = static_cast<float*>(juno_lm_take(static_cast<uint32_t>(juno_lm_width) * 4u));
  return juno_lm_read(reader, gains, static_cast<uint32_t>(juno_lm_width) * 4u) ? gains : nullptr;
}

extern "C" int32_t juno_language_model_release() {
  if (juno_lm_block != nullptr) heap_caps_free(juno_lm_block);
  juno_lm_block = nullptr;
  juno_lm_block_used = 0;
  juno_lm_width = juno_lm_layer_count = juno_lm_heads = juno_lm_head_width = juno_lm_hidden = 0;
  juno_lm_answering = false;
  return 0;
}

extern "C" int32_t juno_language_model_load(int32_t handle) {
  JunoPsramSlot* slot = juno_psram_find(handle);
  if (slot == nullptr) return -1;
  juno_language_model_release();
  JunoLmReader reader = {static_cast<const uint8_t*>(slot->pointer), slot->capacity, 0};
  char magic[4];
  if (!juno_lm_read(reader, magic, 4) || memcmp(magic, "TNY2", 4) != 0) return -2;
  int32_t width = juno_lm_read16(reader), layers = juno_lm_read16(reader), heads = juno_lm_read16(reader);
  int32_t hidden = juno_lm_read16(reader);
  if (width < 8 || width > JUNO_LM_MAX_WIDTH || layers < 1 || layers > JUNO_LM_MAX_LAYERS || heads < 1
      || width % heads != 0 || hidden < 8 || hidden > JUNO_LM_MAX_HIDDEN) return -2;
  uint32_t per_layer = 2u * juno_lm_align(static_cast<uint32_t>(width) * 4u) + 4u * juno_lm_table_bytes(width, width)
      + juno_lm_table_bytes(hidden, width) + juno_lm_table_bytes(width, hidden)
      + 2u * juno_lm_align(static_cast<uint32_t>(JUNO_LM_CONTEXT * width)) + 2u * JUNO_LM_CONTEXT * 4u;
  uint32_t total = juno_lm_table_bytes(JUNO_LM_VOCAB, width) + juno_lm_table_bytes(JUNO_LM_CONTEXT, width)
      + static_cast<uint32_t>(layers) * per_layer + juno_lm_align(static_cast<uint32_t>(width) * 4u);
  juno_lm_block = static_cast<uint8_t*>(heap_caps_malloc(total, JUNO_PSRAM_CAPS));
  if (juno_lm_block == nullptr) return -3;
  juno_lm_width = width;
  juno_lm_hidden = hidden;
  bool ok = juno_lm_read_table(reader, juno_lm_embedding, JUNO_LM_VOCAB, width)
      && juno_lm_read_table(reader, juno_lm_position_table, JUNO_LM_CONTEXT, width);
  for (int32_t l = 0; l < layers && ok; l++) {
    JunoLmLayer& layer = juno_lm_layers[l];
    layer.attention_gain = juno_lm_read_gains(reader);
    ok = layer.attention_gain != nullptr && juno_lm_read_table(reader, layer.query, width, width)
        && juno_lm_read_table(reader, layer.key, width, width) && juno_lm_read_table(reader, layer.value, width, width)
        && juno_lm_read_table(reader, layer.output, width, width);
    if (!ok) break;
    layer.feed_forward_gain = juno_lm_read_gains(reader);
    ok = layer.feed_forward_gain != nullptr && juno_lm_read_table(reader, layer.up, hidden, width)
        && juno_lm_read_table(reader, layer.down, width, hidden);
    layer.key_cache = static_cast<int8_t*>(juno_lm_take(static_cast<uint32_t>(JUNO_LM_CONTEXT * width)));
    layer.value_cache = static_cast<int8_t*>(juno_lm_take(static_cast<uint32_t>(JUNO_LM_CONTEXT * width)));
    layer.key_scales = static_cast<float*>(juno_lm_take(JUNO_LM_CONTEXT * 4u));
    layer.value_scales = static_cast<float*>(juno_lm_take(JUNO_LM_CONTEXT * 4u));
  }
  if (ok) {
    juno_lm_final_gain = juno_lm_read_gains(reader);
    ok = juno_lm_final_gain != nullptr;
  }
  if (!ok) {
    juno_language_model_release();
    return -2;
  }
  juno_lm_layer_count = layers;
  juno_lm_heads = heads;
  juno_lm_head_width = width / heads;
  juno_lm_attention_divisor = sqrtf(static_cast<float>(juno_lm_head_width));
  return 0;
}

extern "C" int32_t juno_language_model_is_loaded() { return juno_lm_layer_count > 0 ? 1 : 0; }

extern "C" int32_t juno_language_model_width() { return juno_lm_width; }

extern "C" int32_t juno_language_model_layers() { return juno_lm_layer_count; }

static void juno_lm_multiply(const JunoLmTable& table, const float* x, float* y) {
  const int8_t* row = table.weights;
  for (int32_t r = 0; r < table.rows; r++) {
    float sum = 0.0f;
    for (int32_t c = 0; c < table.cols; c++) sum += static_cast<float>(row[c]) * x[c];
    y[r] = sum * table.scale[r];
    row += table.cols;
  }
}

static void juno_lm_normalize(const float* x, const float* gain, float* y) {
  float sum = 0.0f;
  for (int32_t i = 0; i < juno_lm_width; i++) sum += x[i] * x[i];
  float scale = 1.0f / sqrtf(sum / juno_lm_width + 1e-5f);
  for (int32_t i = 0; i < juno_lm_width; i++) y[i] = x[i] * scale * gain[i];
}

static float juno_lm_cache(int8_t* target, const float* source) {
  float largest = 0.0f;
  for (int32_t i = 0; i < juno_lm_width; i++) largest = fmaxf(largest, fabsf(source[i]));
  if (largest < 1e-20f) largest = 1e-20f;
  float factor = 127.0f / largest;
  for (int32_t i = 0; i < juno_lm_width; i++) {
    float value = source[i] * factor;
    int32_t level = static_cast<int32_t>(value < 0 ? value - 0.5f : value + 0.5f);
    target[i] = static_cast<int8_t>(level < -127 ? -127 : (level > 127 ? 127 : level));
  }
  return largest / 127.0f;
}

static void juno_lm_attend(JunoLmLayer& layer, int32_t head, int32_t position) {
  int32_t base = head * juno_lm_head_width;
  float largest = -1e30f;
  for (int32_t j = 0; j <= position; j++) {
    const int8_t* key = layer.key_cache + j * juno_lm_width + base;
    float score = 0.0f;
    for (int32_t i = 0; i < juno_lm_head_width; i++) score += juno_lm_query[base + i] * key[i];
    score = score * layer.key_scales[j] / juno_lm_attention_divisor;
    juno_lm_weights[j] = score;
    if (score > largest) largest = score;
  }
  float sum = 0.0f;
  for (int32_t j = 0; j <= position; j++) {
    juno_lm_weights[j] = juno_lm_weights[j] < -60.0f ? 0.0f : expf(juno_lm_weights[j] - largest);
    sum += juno_lm_weights[j];
  }
  for (int32_t i = 0; i < juno_lm_head_width; i++) juno_lm_attended[base + i] = 0.0f;
  for (int32_t j = 0; j <= position; j++) {
    if (juno_lm_weights[j] == 0.0f) continue;
    const int8_t* value = layer.value_cache + j * juno_lm_width + base;
    float scale = juno_lm_weights[j] * layer.value_scales[j] / sum;
    for (int32_t i = 0; i < juno_lm_head_width; i++) juno_lm_attended[base + i] += scale * value[i];
  }
}

static void juno_lm_step(int32_t token, int32_t position) {
  const int8_t* embedding = juno_lm_embedding.weights + token * juno_lm_width;
  const int8_t* place = juno_lm_position_table.weights + position * juno_lm_width;
  float embedding_scale = juno_lm_embedding.scale[token], place_scale = juno_lm_position_table.scale[position];
  for (int32_t i = 0; i < juno_lm_width; i++) juno_lm_state[i] = embedding[i] * embedding_scale + place[i] * place_scale;
  for (int32_t l = 0; l < juno_lm_layer_count; l++) {
    JunoLmLayer& layer = juno_lm_layers[l];
    juno_lm_normalize(juno_lm_state, layer.attention_gain, juno_lm_normalized);
    juno_lm_multiply(layer.key, juno_lm_normalized, juno_lm_temporary);
    layer.key_scales[position] = juno_lm_cache(layer.key_cache + position * juno_lm_width, juno_lm_temporary);
    juno_lm_multiply(layer.value, juno_lm_normalized, juno_lm_temporary);
    layer.value_scales[position] = juno_lm_cache(layer.value_cache + position * juno_lm_width, juno_lm_temporary);
    juno_lm_multiply(layer.query, juno_lm_normalized, juno_lm_query);
    for (int32_t h = 0; h < juno_lm_heads; h++) juno_lm_attend(layer, h, position);
    juno_lm_multiply(layer.output, juno_lm_attended, juno_lm_temporary);
    for (int32_t i = 0; i < juno_lm_width; i++) juno_lm_state[i] += juno_lm_temporary[i];
    juno_lm_normalize(juno_lm_state, layer.feed_forward_gain, juno_lm_normalized);
    juno_lm_multiply(layer.up, juno_lm_normalized, juno_lm_hidden_values);
    for (int32_t i = 0; i < juno_lm_hidden; i++) {
      if (juno_lm_hidden_values[i] < 0.0f) juno_lm_hidden_values[i] = 0.0f;
    }
    juno_lm_multiply(layer.down, juno_lm_hidden_values, juno_lm_temporary);
    for (int32_t i = 0; i < juno_lm_width; i++) juno_lm_state[i] += juno_lm_temporary[i];
  }
  juno_lm_normalize(juno_lm_state, juno_lm_final_gain, juno_lm_normalized);
  juno_lm_multiply(juno_lm_embedding, juno_lm_normalized, juno_lm_scores);
}

static int32_t juno_lm_pick() {
  int32_t best = 0;
  for (int32_t i = 1; i < JUNO_LM_VOCAB; i++) {
    if (juno_lm_scores[i] > juno_lm_scores[best]) best = i;
  }
  float sum = 0.0f;
  for (int32_t i = 0; i < JUNO_LM_VOCAB; i++) {
    float exponent = juno_lm_scores[i] - juno_lm_scores[best];
    sum += expf(exponent < -60.0f ? -60.0f : exponent);
  }
  juno_lm_confidence = 1.0f / sum;
  if (juno_lm_creativity == 0) return best;
  float temperature = juno_lm_creativity / 5.0f, total = 0.0f;
  for (int32_t i = 0; i < JUNO_LM_VOCAB; i++) {
    float exponent = (juno_lm_scores[i] - juno_lm_scores[best]) / temperature;
    float weight = expf(exponent < -60.0f ? -60.0f : (exponent > 0.0f ? 0.0f : exponent));
    juno_lm_weights[i] = weight < 0.15f ? 0.0f : weight;
    total += juno_lm_weights[i];
  }
  float remaining = (static_cast<float>(esp_random()) / 4294967295.0f) * total;
  for (int32_t i = 0; i < JUNO_LM_VOCAB; i++) {
    remaining -= juno_lm_weights[i];
    if (remaining < 0.0f && juno_lm_weights[i] > 0.0f) return i;
  }
  return best;
}

static int32_t juno_lm_ask(const char* text, int32_t length, int32_t creativity) {
  if (juno_lm_layer_count == 0) return -4;
  if (creativity < 0 || creativity > 9) return -5;
  // The same normalization as Tny2Vocabulary.question: cut to 72 characters, lower-case, ? ! , ; " and non-printable
  // characters as spaces, runs of spaces collapsed, none at either end.
  char question[JUNO_LM_MAX_QUESTION];
  int32_t size = 0;
  for (int32_t i = 0; i < length && i < JUNO_LM_MAX_QUESTION && text[i] != '\0'; i++) {
    unsigned char c = static_cast<unsigned char>(text[i]);
    if (c < 32 || c > 126 || c == '?' || c == '!' || c == ',' || c == ';' || c == '"') c = ' ';
    if (c >= 'A' && c <= 'Z') c = static_cast<unsigned char>(c + ('a' - 'A'));
    if (c == ' ' && (size == 0 || question[size - 1] == ' ')) continue;
    question[size++] = static_cast<char>(c);
  }
  while (size > 0 && question[size - 1] == ' ') size--;
  if (size == 0) return -5;
  juno_lm_creativity = creativity;
  int32_t position = 0;
  for (; position < size; position++) {
    juno_lm_step(static_cast<unsigned char>(question[position]) - JUNO_LM_TOKEN_OFFSET, position);
  }
  juno_lm_step(JUNO_LM_END, position++);
  juno_lm_pending_length = 0;
  juno_lm_pending_next = 0;
  juno_lm_pending_ended = false;
  juno_lm_fallback = nullptr;
  float lowest = 1.0f;
  for (int32_t picks = 0; picks < JUNO_LM_LOOKAHEAD && position < JUNO_LM_CONTEXT; picks++, position++) {
    int32_t token = juno_lm_pick();
    if (juno_lm_confidence < lowest) lowest = juno_lm_confidence;
    if (token <= JUNO_LM_END) {
      juno_lm_pending_ended = true;
      break;
    }
    juno_lm_pending[juno_lm_pending_length++] = static_cast<char>(token + JUNO_LM_TOKEN_OFFSET);
    juno_lm_step(token, position);
  }
  if (position >= JUNO_LM_CONTEXT) juno_lm_pending_ended = true;
  if (lowest < JUNO_LM_CONFIDENCE_FLOOR) juno_lm_fallback = JUNO_LM_UNKNOWN;
  juno_lm_answer_position = position;
  juno_lm_answering = true;
  return 0;
}

extern "C" int32_t juno_language_model_ask(const char* question, int32_t creativity) {
  if (question == nullptr) return -5;
  return juno_lm_ask(question, JUNO_LM_MAX_QUESTION, creativity);
}

extern "C" int32_t juno_language_model_ask_bytes(const uint8_t* question, int32_t capacity, int32_t length,
                                                 int32_t creativity) {
  if (question == nullptr || length < 0 || capacity < 0 || length > capacity) return -5;
  return juno_lm_ask(reinterpret_cast<const char*>(question), length, creativity);
}

extern "C" int32_t juno_language_model_next_char() {
  if (!juno_lm_answering) return -1;
  if (juno_lm_fallback != nullptr) {
    char c = *juno_lm_fallback;
    if (c == '\0') {
      juno_lm_answering = false;
      return -1;
    }
    juno_lm_fallback++;
    return static_cast<unsigned char>(c);
  }
  if (juno_lm_pending_next < juno_lm_pending_length) {
    return static_cast<unsigned char>(juno_lm_pending[juno_lm_pending_next++]);
  }
  if (juno_lm_pending_ended || juno_lm_answer_position >= JUNO_LM_CONTEXT) {
    juno_lm_answering = false;
    return -1;
  }
  int32_t token = juno_lm_pick();
  if (token <= JUNO_LM_END) {
    juno_lm_answering = false;
    return -1;
  }
  juno_lm_step(token, juno_lm_answer_position++);
  return token + JUNO_LM_TOKEN_OFFSET;
}
