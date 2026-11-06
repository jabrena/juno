// The AI Thinker ESP32-CAM's OV2640 through the core's esp_camera driver, with the board's fixed wiring. Frames are JPEG
// in PSRAM, which the board always has (its build defines BOARD_HAS_PSRAM), at QQVGA so a frame fits a small buffer.
// The driver owns its frame buffer; juno_camera_capture copies the frame out and returns the buffer at once.
static bool juno_camera_ready = false;

extern "C" int32_t juno_camera_begin() {
  if (juno_camera_ready) return 1;
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
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_QQVGA;
  config.jpeg_quality = 12;
  config.fb_count = 2;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.grab_mode = CAMERA_GRAB_LATEST;
  juno_camera_ready = esp_camera_init(&config) == ESP_OK;
  if (juno_camera_ready) {
    // The module is mounted upside down: a vertical flip plus a horizontal mirror is a 180-degree rotation.
    sensor_t* sensor = esp_camera_sensor_get();
    if (sensor != nullptr) {
      sensor->set_vflip(sensor, 1);
      sensor->set_hmirror(sensor, 1);
    }
  }
  return juno_camera_ready ? 1 : 0;
}

extern "C" int32_t juno_camera_capture(uint8_t* buffer, int32_t capacity) {
  if (!juno_camera_ready || buffer == nullptr || capacity <= 0) return -1;
  camera_fb_t* frame = esp_camera_fb_get();
  if (frame == nullptr) return -1;
  int32_t length = frame->len <= static_cast<size_t>(capacity) ? static_cast<int32_t>(frame->len) : -1;
  if (length > 0) memcpy(buffer, frame->buf, frame->len);
  esp_camera_fb_return(frame);
  return length;
}
