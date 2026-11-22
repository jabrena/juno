// The AI Thinker ESP32-CAM's OV2640 through the core's esp_camera driver, with the board's fixed wiring. Frames are JPEG
// in PSRAM, which the board always has (its build defines BOARD_HAS_PSRAM). The driver starts at SVGA, so its frame
// buffers fit the largest size juno_camera_set_frame_size offers, then drops to QQVGA so a frame fits a small buffer.
// The driver owns its frame buffer; juno_camera_capture copies the frame out and returns the buffer at once.
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
  // 10 MHz rather than 20: OV2640 modules on worn ribbons fail or hang their SCCB probe at 20 MHz.
  config.xclk_freq_hz = 10000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_SVGA;
  config.jpeg_quality = 12;
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
  if (sensor->set_framesize(sensor, FRAMESIZE_QQVGA) != 0) {
    esp_camera_deinit();
    return 0;
  }
  juno_camera_ready = true;
  return 1;
}

// FrameSize in ordinal order: QQVGA 160x120, QVGA 320x240, VGA 640x480, SVGA 800x600.
static const framesize_t juno_camera_frame_sizes[] = {FRAMESIZE_QQVGA, FRAMESIZE_QVGA, FRAMESIZE_VGA, FRAMESIZE_SVGA};

extern "C" int32_t juno_camera_set_frame_size(int32_t frameSize) {
  constexpr int32_t count = sizeof(juno_camera_frame_sizes) / sizeof(juno_camera_frame_sizes[0]);
  if (!juno_camera_ready || frameSize < 0 || frameSize >= count) return 0;
  sensor_t* sensor = esp_camera_sensor_get();
  if (sensor == nullptr || sensor->set_framesize(sensor, juno_camera_frame_sizes[frameSize]) != 0) return 0;
  // Frames already queued were taken at the old size: drop them, so the next capture is at the new one.
  for (int32_t i = 0; i < 2; i++) {
    camera_fb_t* stale = esp_camera_fb_get();
    if (stale != nullptr) esp_camera_fb_return(stale);
  }
  return 1;
}

extern "C" int32_t juno_camera_capture(uint8_t* buffer, int32_t capacity) {
  if (!juno_camera_ready || buffer == nullptr || capacity <= 0) return -1;
  camera_fb_t* frame = esp_camera_fb_get();
  if (frame == nullptr) return -1;
  int32_t length = -1;
  if (frame->buf != nullptr && frame->len <= static_cast<size_t>(capacity)) {
    memcpy(buffer, frame->buf, frame->len);
    length = static_cast<int32_t>(frame->len);
  }
  esp_camera_fb_return(frame);
  return length;
}
