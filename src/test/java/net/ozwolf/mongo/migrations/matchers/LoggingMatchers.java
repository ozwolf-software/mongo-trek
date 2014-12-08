package net.ozwolf.mongo.migrations.matchers;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.mockito.ArgumentCaptor;

public class LoggingMatchers {
    private final static ArgumentCaptor<ILoggingEvent> CAPTOR = ArgumentCaptor.forClass(ILoggingEvent.class);

    public static TypeSafeMatcher<ILoggingEvent> loggedMessage(final String message) {
        return new TypeSafeMatcher<ILoggingEvent>() {
            @Override
            protected boolean matchesSafely(ILoggingEvent event) {
                return event.getFormattedMessage().equals(message);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("message = <%s>", message));
            }

            @Override
            protected void describeMismatchSafely(ILoggingEvent event, Description mismatchDescription) {
                mismatchDescription.appendText(String.format("message = <%s>", event.getFormattedMessage()));
            }
        };
    }
}
