package net.ozwolf.mongo.migrations.internal.util;

import net.ozwolf.mongo.migrations.internal.util.strict.StrictOperator;

import java.util.List;
import java.util.Map;

public class StrictJsonUtils {
    public static Map<String, Object> interpolate(Map<String, Object> command) {
        interpolateLeaf(command);
        return command;
    }

    @SuppressWarnings("unchecked")
    private static void interpolateLeaf(Map<String, Object> leaf) {
        try {
            for (String key : leaf.keySet()) {
                Object value = leaf.get(key);
                StrictOperator operator = StrictOperator.findFor(value).orElse(null);
                if (operator != null) {
                    leaf.put(key, operator.interpolate(value));
                } else {
                    if (value instanceof List) {
                        List<Map<String, Object>> v = (List<Map<String, Object>>) value;
                        for (Map<String, Object> d : v) interpolateLeaf(d);
                    } else if (value instanceof Map) {
                        interpolateLeaf((Map<String, Object>) value);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
