// The core's TinyUSB HID mouse under the Mouse library's name, so the shared juno_mouse_* bindings drive it. Its
// constructor registers the HID interface during static initialization, before the core starts USB, so the host sees
// one composite device with the CDC serial port; USB.begin() is a no-op once the core has started it.
// The HID class only sends reports once begun, so the first move() begins it: as on every other board, a program can
// move the mouse without calling Mouse.begin() first.
class JunoUsbMouse {
 public:
  void begin() {
    if (!begun) {
      mouse.begin();
      USB.begin();
      begun = true;
    }
  }

  void move(signed char x, signed char y) {
    begin();
    mouse.move(x, y);
  }

 private:
  USBHIDMouse mouse;
  bool begun = false;
};

static JunoUsbMouse Mouse;
