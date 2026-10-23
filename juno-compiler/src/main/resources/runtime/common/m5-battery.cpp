extern "C" int32_t juno_m5_battery_level_percent() {
  juno_m5_begin(false);
  return M5Cardputer.Power.getBatteryLevel();
}

extern "C" int32_t juno_m5_battery_voltage_millivolts() {
  juno_m5_begin(false);
  return M5Cardputer.Power.getBatteryVoltage();
}
