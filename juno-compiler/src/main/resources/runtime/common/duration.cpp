// java.time.Duration: a one-word handle that is the duration in milliseconds. Durations are the timeouts a program
// hands to StructuredTaskScope and java.net.http, so the 24.8 days a signed 32-bit word holds is plenty; a longer
// one is clamped to that, and each consumer checks the range it accepts (a scope timeout, for one, must be positive).
static const int64_t JUNO_DURATION_MAX_MILLIS = 0x7FFFFFFFLL;

static int32_t juno_duration_clamp(int64_t millisValue) {
  if (millisValue > JUNO_DURATION_MAX_MILLIS) return static_cast<int32_t>(JUNO_DURATION_MAX_MILLIS);
  if (millisValue < -JUNO_DURATION_MAX_MILLIS) return static_cast<int32_t>(-JUNO_DURATION_MAX_MILLIS);
  return static_cast<int32_t>(millisValue);
}

extern "C" int32_t juno_duration_of_millis(int64_t millisValue) {
  return juno_duration_clamp(millisValue);
}

extern "C" int32_t juno_duration_of_seconds(int64_t seconds) {
  if (seconds > JUNO_DURATION_MAX_MILLIS / 1000LL) return static_cast<int32_t>(JUNO_DURATION_MAX_MILLIS);
  if (seconds < -JUNO_DURATION_MAX_MILLIS / 1000LL) return static_cast<int32_t>(-JUNO_DURATION_MAX_MILLIS);
  return juno_duration_clamp(seconds * 1000LL);
}

extern "C" int64_t juno_duration_to_millis(int32_t duration) {
  return static_cast<int64_t>(duration);
}
