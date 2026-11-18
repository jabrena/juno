#include <cassert>
#include <cstdint>
#include <cstring>
#include <esp_camera.h>

// ESP32_CAMERA_GRAYSCALE

// Host implementation of the esp32-camera driver. Each test sets what the next call returns and reads back what the
// runtime asked for. frameGets counts frames handed out, so each one must come back through frameReturns.
static bool psramFree = true;
static bool initOk = true;
static bool frameAvailable = true;
static pixformat_t initFormat;
static framesize_t initSize;
static int initCalls, deinitCalls, flipCalls, mirrorCalls, frameGets, frameReturns;
static camera_fb_t frame;
static uint8_t source[160 * 120];

static int setVflip(sensor_t*, int) { ++flipCalls; return 0; }
static int setHmirror(sensor_t*, int) { ++mirrorCalls; return 0; }
static sensor_t sensorState = {setVflip, setHmirror};

size_t heap_caps_get_largest_free_block(uint32_t) { return psramFree ? 4u * 1024u * 1024u : 0u; }
esp_err_t esp_camera_init(const camera_config_t* config) {
  ++initCalls;
  initFormat = config->pixel_format;
  initSize = config->frame_size;
  return initOk ? ESP_OK : ESP_FAIL;
}
esp_err_t esp_camera_deinit() { ++deinitCalls; return ESP_OK; }
sensor_t* esp_camera_sensor_get() { return &sensorState; }
camera_fb_t* esp_camera_fb_get() {
  if (!frameAvailable) return nullptr;
  ++frameGets;
  return &frame;
}
void esp_camera_fb_return(camera_fb_t*) { ++frameReturns; }

static void presentFrame(pixformat_t format, size_t width, size_t height, size_t length) {
  frame.buf = source;
  frame.len = length;
  frame.width = width;
  frame.height = height;
  frame.format = format;
}

static int capture(uint8_t* pixels, int32_t capacity, int32_t width, int32_t height) {
  return juno_camera_capture_grayscale(pixels, capacity, width, height);
}

// 160 pixels wide: columns below 81 are black, the rest white. A 64-wide output then has exact values: 0, a
// half-and-half pixel at column 32 (pixels 80 and 81 average to 100), and 200.
static void fillVerticalEdgeAt81() {
  for (int y = 0; y < 120; ++y) {
    for (int x = 0; x < 160; ++x) source[y * 160 + x] = x >= 81 ? 200 : 0;
  }
}

// 120 rows high: rows below 59 are black, the rest white. The output row 31 covers source rows 58 and 59 (average 100).
static void fillHorizontalEdgeAt59() {
  for (int y = 0; y < 120; ++y) {
    for (int x = 0; x < 160; ++x) source[y * 160 + x] = y >= 59 ? 200 : 0;
  }
}

int main() {
  constexpr int W = 64;
  constexpr int H = 64;
  static uint8_t out[W * H + 16];
  static uint8_t big[200 * 150 + 16];
  memset(out, 0xEE, sizeof out);

  // Not started: false, and the driver is never asked for a frame.
  assert(capture(out, W * H, W, H) == 0);
  assert(frameGets == 0);

  // No PSRAM: begin fails before the driver is initialised.
  psramFree = false;
  assert(juno_camera_begin() == 0);
  assert(initCalls == 0);
  psramFree = true;

  // The driver refuses to start: begin fails, the driver is torn down, and capture stays false.
  initOk = false;
  assert(juno_camera_begin() == 0);
  assert(deinitCalls == 1);
  assert(capture(out, W * H, W, H) == 0);
  initOk = true;

  // A good start configures grayscale at QQVGA and mirrors the module, and a second begin does not re-initialise.
  assert(juno_camera_begin() == 1);
  assert(initFormat == PIXFORMAT_GRAYSCALE);
  assert(initSize == FRAMESIZE_QQVGA);
  assert(flipCalls == 1 && mirrorCalls == 1);
  assert(juno_camera_begin() == 1 && initCalls == 2);

  // A buffer shorter than width x height is refused before any frame is taken, and nothing is written.
  assert(capture(out, W * H - 1, W, H) == 0);
  assert(frameGets == 0);
  for (int i = 0; i < W * H + 16; ++i) assert(out[i] == 0xEE);

  // No frame yet: false, and nothing to return.
  frameAvailable = false;
  int getsBefore = frameGets, returnsBefore = frameReturns;
  assert(capture(out, W * H, W, H) == 0);
  assert(frameGets == getsBefore && frameReturns == returnsBefore);
  frameAvailable = true;

  // A frame that is not grayscale, one that is too short, and one with no buffer: each is false and still returned.
  fillVerticalEdgeAt81();
  presentFrame(PIXFORMAT_JPEG, 160, 120, 19200);
  returnsBefore = frameReturns;
  assert(capture(out, W * H, W, H) == 0);
  assert(frameReturns == returnsBefore + 1);

  presentFrame(PIXFORMAT_GRAYSCALE, 160, 120, 100);
  returnsBefore = frameReturns;
  assert(capture(out, W * H, W, H) == 0);
  assert(frameReturns == returnsBefore + 1);

  presentFrame(PIXFORMAT_GRAYSCALE, 160, 120, 19200);
  frame.buf = nullptr;
  returnsBefore = frameReturns;
  assert(capture(out, W * H, W, H) == 0);
  assert(frameReturns == returnsBefore + 1);
  frame.buf = source;

  // Exact downscale along x: 0 for columns 0 to 31, 100 for column 32, 200 for columns 33 to 63.
  presentFrame(PIXFORMAT_GRAYSCALE, 160, 120, 19200);
  memset(out, 0xEE, sizeof out);
  assert(capture(out, W * H, W, H) == 1);
  for (int y = 0; y < H; ++y) {
    for (int x = 0; x < W; ++x) {
      int expected = x < 32 ? 0 : (x == 32 ? 100 : 200);
      assert(out[y * W + x] == expected);
    }
  }
  for (int i = W * H; i < W * H + 16; ++i) assert(out[i] == 0xEE);

  // Exact downscale along y: 0 for rows 0 to 30, 100 for row 31, 200 for rows 32 to 63.
  fillHorizontalEdgeAt59();
  memset(out, 0xEE, sizeof out);
  assert(capture(out, W * H, W, H) == 1);
  for (int y = 0; y < H; ++y) {
    for (int x = 0; x < W; ++x) {
      int expected = y < 31 ? 0 : (y == 31 ? 100 : 200);
      assert(out[y * W + x] == expected);
    }
  }

  // Upscaling to a larger output stays inside its buffer and every pixel is one of the source values.
  fillVerticalEdgeAt81();
  presentFrame(PIXFORMAT_GRAYSCALE, 160, 120, 19200);
  memset(big, 0xEE, sizeof big);
  assert(capture(big, 200 * 150, 200, 150) == 1);
  for (int i = 0; i < 200 * 150; ++i) assert(big[i] == 0 || big[i] == 200);
  for (int i = 200 * 150; i < 200 * 150 + 16; ++i) assert(big[i] == 0xEE);

  // Every frame the driver handed out came back, on every path.
  assert(frameGets == frameReturns);
  return 0;
}
