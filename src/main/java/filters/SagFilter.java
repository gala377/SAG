package filters;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class SagFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        boolean isSag = event.getMarker().contains("Sag");
        if (isSag) {
            return FilterReply.ACCEPT;
        } else {
            return FilterReply.DENY;
        }
    }
}