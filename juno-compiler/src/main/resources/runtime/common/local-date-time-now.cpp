
static constexpr uint32_t JUNO_LOCAL_DATE_TIME_RETRY_MILLIS = 60000u;
static constexpr uint32_t JUNO_LOCAL_DATE_TIME_REFRESH_MILLIS = 3600000u;
static bool juno_local_date_time_synchronized = false;
static bool juno_local_date_time_attempted = false;
static uint32_t juno_local_date_time_base_millis = 0;
static uint32_t juno_local_date_time_last_attempt_millis = 0;
static JunoLocalDateTime juno_local_date_time_base = {
    JUNO_LOCAL_DATE_TIME_MAGIC, 1970, 1, 1, 0, 0, 0};

static bool juno_local_date_time_leap_year(int32_t year) {
  return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
}

static int32_t juno_local_date_time_days_in_month(int32_t year, int32_t month) {
  static const uint8_t lengths[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  return month == 2 && juno_local_date_time_leap_year(year) ? 29 : lengths[month - 1];
}

static JunoLocalDateTime juno_local_date_time_advance(JunoLocalDateTime value, uint32_t seconds) {
  seconds += static_cast<uint32_t>(value.second);
  value.second = static_cast<int32_t>(seconds % 60u);
  seconds /= 60u;
  seconds += static_cast<uint32_t>(value.minute);
  value.minute = static_cast<int32_t>(seconds % 60u);
  seconds /= 60u;
  seconds += static_cast<uint32_t>(value.hour);
  value.hour = static_cast<int32_t>(seconds % 24u);
  uint32_t days = seconds / 24u;
  while (days-- > 0u) {
    value.day++;
    if (value.day > juno_local_date_time_days_in_month(value.year, value.month)) {
      value.day = 1;
      value.month++;
      if (value.month > 12) {
        value.month = 1;
        value.year++;
      }
    }
  }
  return value;
}

${JUNO_LOCAL_DATE_TIME_FETCH}extern "C" int32_t juno_local_date_time_now() {
  if (juno_wifi_status() != 3) return juno_local_date_time_min();
  uint32_t currentMillis = static_cast<uint32_t>(millis());
  if (!juno_local_date_time_synchronized) {
    if (!juno_local_date_time_attempted
        || currentMillis - juno_local_date_time_last_attempt_millis >= JUNO_LOCAL_DATE_TIME_RETRY_MILLIS) {
      juno_local_date_time_attempted = true;
      juno_local_date_time_last_attempt_millis = currentMillis;
      JunoLocalDateTime fetched;
      if (juno_local_date_time_fetch(&fetched)) {
        juno_local_date_time_base = fetched;
        juno_local_date_time_base_millis = static_cast<uint32_t>(millis());
        juno_local_date_time_synchronized = true;
      }
    }
    if (!juno_local_date_time_synchronized) return juno_local_date_time_min();
    currentMillis = static_cast<uint32_t>(millis());
  }

  uint32_t elapsedMillis = currentMillis - juno_local_date_time_base_millis;
  JunoLocalDateTime current = juno_local_date_time_advance(
      juno_local_date_time_base, elapsedMillis / 1000u);
  if (elapsedMillis >= JUNO_LOCAL_DATE_TIME_REFRESH_MILLIS) {
    JunoLocalDateTime fetched;
    if (juno_local_date_time_fetch(&fetched)) current = fetched;
    juno_local_date_time_base = current;
    juno_local_date_time_base_millis = static_cast<uint32_t>(millis());
  }
  return juno_local_date_time_new(current);
}
