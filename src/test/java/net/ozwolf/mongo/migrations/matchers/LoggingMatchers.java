package net.ozwolf.mongo.migrations.matchers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.assertj.core.api.Condition;

public class LoggingMatchers {
    public static Condition<ILoggingEvent> loggedMessage(final String message) {
        return new Condition<>(
                e -> e.getFormattedMessage().equals(message),
                String.format("message = <%s>", message)
        );
    }
}
