// The AI Thinker ESP32-CAM's OV2640 in grayscale, through the core's esp_camera driver. A program that calls
// Camera.captureGrayscale is compiled with this file instead of camera.cpp, so the sensor is initialized once in
// grayscale and never switches to JPEG. Frames are 8-bit pixels in PSRAM at QQVGA (160x120); the driver owns its frame
// buffer, and juno_camera_capture_grayscale averages it into the program's buffer and returns the buffer at once.
#include <esp_heap_caps.h>
#include <string.h>

static bool juno_camera_ready = false;

extern "C" int32_t juno_camera_begin() {
  if (juno_camera_ready) return 1;
  constexpr uint32_t juno_psram_caps = MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT;
  if (heap_caps_get_largest_free_block(juno_psram_caps) == 0u) return 0;

  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = 5;
  config.pin_d1 = 18;
  config.pin_d2 = 19;
  config.pin_d3 = 21;
  config.pin_d4 = 36;
  config.pin_d5 = 39;
  config.pin_d6 = 34;
  config.pin_d7 = 35;
  config.pin_xclk = 0;
  config.pin_pclk = 22;
  config.pin_vsync = 25;
  config.pin_href = 23;
  config.pin_sccb_sda = 26;
  config.pin_sccb_scl = 27;
  config.pin_pwdn = 32;
  config.pin_reset = -1;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_GRAYSCALE;
  config.frame_size = FRAMESIZE_QQVGA;
  config.fb_count = 2;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.grab_mode = CAMERA_GRAB_LATEST;
  if (esp_camera_init(&config) != ESP_OK) {
    esp_camera_deinit();
    return 0;
  }

  // The module is mounted upside down: a vertical flip plus a horizontal mirror is a 180-degree rotation.
  sensor_t* sensor = esp_camera_sensor_get();
  if (sensor == nullptr) {
    esp_camera_deinit();
    return 0;
  }
  sensor->set_vflip(sensor, 1);
  sensor->set_hmirror(sensor, 1);
  juno_camera_ready = true;
  return 1;
}

// Averages the source block behind each target pixel with integer arithmetic. A target pixel always covers at least one
// source pixel, so upscaling falls back to the nearest source pixel instead of an empty block.
static void juno_downscale_grayscale(const uint8_t* source, int32_t sourceWidth, int32_t sourceHeight, uint8_t* target,
                                     int32_t width, int32_t height) {
  for (int32_t y = 0; y < height; ++y) {
    int32_t top = (y * sourceHeight) / height;
    int32_t bottom = ((y + 1) * sourceHeight) / height;
    if (bottom <= top) bottom = top + 1;
    if (bottom > sourceHeight) bottom = sourceHeight;
    for (int32_t x = 0; x < width; ++x) {
      int32_t left = (x * sourceWidth) / width;
      int32_t right = ((x + 1) * sourceWidth) / width;
      if (right <= left) right = left + 1;
      if (right > sourceWidth) right = sourceWidth;
      uint32_t sum = 0;
      uint32_t count = 0;
      for (int32_t sy = top; sy < bottom; ++sy) {
        for (int32_t sx = left; sx < right; ++sx) {
          sum += source[sy * sourceWidth + sx];
          ++count;
        }
      }
      target[y * width + x] = static_cast<uint8_t>(sum / count);
    }
  }
}

// Returns 1 when the frame was written into pixels, and 0 otherwise. It never panics: a call that cannot run returns 0.
extern "C" int32_t juno_camera_capture_grayscale(uint8_t* pixels, int32_t capacity, int32_t width, int32_t height) {
  if (!juno_camera_ready || pixels == nullptr || width <= 0 || height <= 0) return 0;
  if (static_cast<int64_t>(capacity) < static_cast<int64_t>(width) * height) return 0;
  camera_fb_t* frame = esp_camera_fb_get();
  if (frame == nullptr) return 0;
  bool written = false;
  if (frame->buf != nullptr && frame->format == PIXFORMAT_GRAYSCALE && frame->width > 0 && frame->height > 0 &&
      frame->len >= static_cast<size_t>(frame->width) * frame->height) {
    juno_downscale_grayscale(frame->buf, static_cast<int32_t>(frame->width), static_cast<int32_t>(frame->height),
                             pixels, width, height);
    written = true;
  }
  esp_camera_fb_return(frame);
  return written ? 1 : 0;
}
