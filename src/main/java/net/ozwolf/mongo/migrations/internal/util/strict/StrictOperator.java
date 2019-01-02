package net.ozwolf.mongo.migrations.internal.util.strict;

import net.ozwolf.mongo.migrations.internal.util.strict.interpolator.DateInterpolator;
import net.ozwolf.mongo.migrations.internal.util.strict.test.DateTest;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public enum StrictOperator {
    DATE(new DateTest(), new DateInterpolator());

    private final Predicate<Object> tester;
    private final Function<Object, Object> interpolator;

    StrictOperator(Predicate<Object> tester, Function<Object, Object> interpolator) {
        this.tester = tester;
        this.interpolator = interpolator;
    }

    public Object interpolate(Object value) {
        return interpolator.apply(value);
    }

    public static Optional<StrictOperator> findFor(Object value) {
        for (StrictOperator operator : values())
            if (operator.tester.test(value))
                return Optional.of(operator);

        return Optional.empty();
    }
}
