package net.ozwolf.mongo.migrations.internal.util.strict.interpolator;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class DateInterpolator implements Function<Object, Object> {
    private final static List<DateTimeFormatter> SUPPORTED_FORMATS = new LinkedList<>();

    static {
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_INSTANT);
        SUPPORTED_FORMATS.add(DateTimeFormatter.BASIC_ISO_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_LOCAL_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_OFFSET_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_LOCAL_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_OFFSET_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_DATE_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_ORDINAL_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.ISO_WEEK_DATE);
        SUPPORTED_FORMATS.add(DateTimeFormatter.RFC_1123_DATE_TIME);
    }


    @SuppressWarnings("unchecked")
    @Override
    public Object apply(Object o) {
        if (!(o instanceof Map))
            return o;

        Map<String, Object> m = (Map<String, Object>) o;
        if (!m.containsKey("$date"))
            return o;

        Object v = m.get("$date");
        if (!(v instanceof String))
            return o;

        String value = (String) v;

        Date parsed = null;
        for (DateTimeFormatter formatter : SUPPORTED_FORMATS) {
            parsed = parseUsing(value, formatter);
            if (parsed != null) break;
        }

        if (parsed == null)
            throw new IllegalArgumentException("Strict $date value of [ " + value + " ] does not match supported date or date-time formats.");

        return parsed;
    }

    private static Date parseUsing(String value, DateTimeFormatter formatter) {
        try {
            DateTimeFormatter output = DateTimeFormatter.ISO_INSTANT;
            TemporalAccessor parsed = formatter.parse(value);
            String formatted = output.format(parsed);

            return Date.from(Instant.parse(formatted));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
