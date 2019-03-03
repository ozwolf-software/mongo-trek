package net.ozwolf.mongo.migrations.internal.util;

import net.ozwolf.mongo.migrations.internal.util.strict.StrictOperator;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

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
                    // We've handled this entity, so time to move on.
                    continue;
                }

                ValueType type = getType(value);
                switch (type) {
                    case OBJECT:
                        interpolateLeaf((Map<String, Object>) value);
                        break;
                    case LITERAL_LIST:
                        leaf.put(key, ((List) value).stream().map(StrictJsonUtils::interpolated).collect(toList()));
                        break;
                    case OBJECT_LIST:
                        ((List<Map<String, Object>>) value).forEach(StrictJsonUtils::interpolateLeaf);
                        break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object interpolated(Object value) {
        return StrictOperator.findFor(value)
                .map(o -> o.interpolate(value))
                .orElse(value);
    }

    private static ValueType getType(Object value) {
        if (value instanceof List) {
            List l = (List) value;
            return isObjectList(l) ? ValueType.OBJECT_LIST : ValueType.LITERAL_LIST;
        } else if (value instanceof Map) {
            return ValueType.OBJECT;
        } else {
            return ValueType.LITERAL;
        }
    }

    private static boolean isObjectList(List collection) {
        if (collection.isEmpty())
            return false;

        Object first = collection.get(0);
        return first instanceof Map;
    }

    private enum ValueType {
        OBJECT,
        LITERAL,
        OBJECT_LIST,
        LITERAL_LIST
    }
}
