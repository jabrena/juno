package io.github.jabrena.juno.intrinsic;

import io.github.jabrena.juno.classfile.MethodRef;

import java.util.Map;

/** The deliberately bounded {@code java.time.LocalDateTime} subset supported on the board. */
public final class LocalDateTimeMethods {
    static final Map<MethodRef, Intrinsic> METHODS = Map.ofEntries(
            Map.entry(new MethodRef("io/github/jabrena/juno/api/InternetClock", "configure",
                    "(Ljava/util/Properties;)Z"), Intrinsic.LOCAL_DATE_TIME_CONFIGURE),
            Map.entry(new MethodRef("java/time/LocalDateTime", "now", "()Ljava/time/LocalDateTime;"),
                    Intrinsic.LOCAL_DATE_TIME_NOW),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getYear", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_YEAR),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getMonthValue", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_MONTH_VALUE),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getDayOfMonth", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_DAY_OF_MONTH),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getHour", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_HOUR),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getMinute", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_MINUTE),
            Map.entry(new MethodRef("java/time/LocalDateTime", "getSecond", "()I"),
                    Intrinsic.LOCAL_DATE_TIME_GET_SECOND),
            Map.entry(new MethodRef("java/time/LocalDateTime", "equals", "(Ljava/lang/Object;)Z"),
                    Intrinsic.LOCAL_DATE_TIME_EQUALS),
            Map.entry(new MethodRef("java/time/LocalDateTime", "toString", "()Ljava/lang/String;"),
                    Intrinsic.LOCAL_DATE_TIME_TO_STRING));

    private LocalDateTimeMethods() {
    }
}
