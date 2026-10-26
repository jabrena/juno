// The core's TinyUSB HID mouse under the Mouse library's name, so the shared juno_mouse_* bindings drive it. Its
// constructor registers the HID interface during static initialization, before the core starts USB, so the host sees
// one composite device with the CDC serial port; USB.begin() is a no-op once the core has started it.
class JunoUsbMouse {
 public:
  void begin() {
    mouse.begin();
    USB.begin();
  }

  void move(signed char x, signed char y) {
    mouse.move(x, y);
  }

 private:
  USBHIDMouse mouse;
};

static JunoUsbMouse Mouse;
