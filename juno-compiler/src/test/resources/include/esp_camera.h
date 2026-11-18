// Host stand-in for the esp32-camera driver. The functions are defined by the harness, which records how the runtime
// calls them and decides what each call returns.
#pragma once
#include <stddef.h>
#include <stdint.h>

typedef int esp_err_t;
#define ESP_OK 0
#define ESP_FAIL (-1)

typedef enum { PIXFORMAT_RGB565, PIXFORMAT_YUV422, PIXFORMAT_GRAYSCALE, PIXFORMAT_JPEG } pixformat_t;
typedef enum { FRAMESIZE_QQVGA = 0, FRAMESIZE_QVGA = 5 } framesize_t;
typedef enum { CAMERA_FB_IN_DRAM = 0, CAMERA_FB_IN_PSRAM = 1 } camera_fb_location_t;
typedef enum { CAMERA_GRAB_WHEN_EMPTY = 0, CAMERA_GRAB_LATEST = 1 } camera_grab_mode_t;
#define LEDC_CHANNEL_0 0
#define LEDC_TIMER_0 0

typedef struct {
  int ledc_channel;
  int ledc_timer;
  int pin_d0, pin_d1, pin_d2, pin_d3, pin_d4, pin_d5, pin_d6, pin_d7;
  int pin_xclk, pin_pclk, pin_vsync, pin_href, pin_sccb_sda, pin_sccb_scl, pin_pwdn, pin_reset;
  int xclk_freq_hz;
  pixformat_t pixel_format;
  framesize_t frame_size;
  int jpeg_quality;
  int fb_count;
  camera_fb_location_t fb_location;
  camera_grab_mode_t grab_mode;
} camera_config_t;

typedef struct {
  uint8_t* buf;
  size_t len;
  size_t width;
  size_t height;
  pixformat_t format;
} camera_fb_t;

typedef struct sensor_s {
  int (*set_vflip)(struct sensor_s* sensor, int enable);
  int (*set_hmirror)(struct sensor_s* sensor, int enable);
} sensor_t;

esp_err_t esp_camera_init(const camera_config_t* config);
esp_err_t esp_camera_deinit();
sensor_t* esp_camera_sensor_get();
camera_fb_t* esp_camera_fb_get();
void esp_camera_fb_return(camera_fb_t* frame);
