package cn.nitrowater.core.lib.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateUtil {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");


    public static long getSecondsUntilMidnight() {
        long now = System.currentTimeMillis() / 1000;
        LocalDate today = LocalDate.now();
        long midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
        return midnight - now;
    }

    public static String standardShanghaiTime() {
        return Instant.now()
                .atZone(SHANGHAI)
                .format(FORMATTER);
    }

    public static String standardShanghaiTime(String expiresAt) {
        Instant instant = Instant.parse(expiresAt);
        return instant.atZone(SHANGHAI).format(FORMATTER);
    }

    public static String formatForFileName(String timeStamp) {
        return standardShanghaiTime()
                .replace(":", "-")
                .replace(" ", "_");
    }
}
