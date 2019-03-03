package net.ozwolf.mongo.migrations.testutils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class DateTimeUtils {
    private final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final static DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.getDefault().toZoneId());

    public static String formatDisplay(String dateTime) {
        return OffsetDateTime.parse(dateTime, DATE_TIME_FORMATTER).format(DISPLAY_FORMATTER);
    }
}
