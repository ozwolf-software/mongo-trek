package net.ozwolf.mongo.migrations.internal.util.strict.test;

import java.util.Map;
import java.util.function.Predicate;

public class DateTest implements Predicate<Object> {
    @SuppressWarnings("unchecked")
    @Override
    public boolean test(Object o) {
        if (!(o instanceof Map))
            return false;

        Map<String, Object> m = (Map<String, Object>) o;
        return m.containsKey("$date");
    }
}
