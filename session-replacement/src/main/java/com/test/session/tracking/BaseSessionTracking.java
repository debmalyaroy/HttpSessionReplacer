package com.test.session.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionIdProvider;
import com.test.session.api.SessionTracking;

/**
 * This base class is for session ID tracking.
 */
public abstract class BaseSessionTracking implements SessionTracking {
    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseSessionTracking.class);
    private static final char SESSION_ID_TIMESTAMP_SEPARATOR = '!';

    protected Map<String, SessionIdProvider> sessionIdProviders = new HashMap<>();

    @Override
    public String newId() {
        LOGGER.debug("Creating new ID.");

        long timestamp = System.currentTimeMillis();
        String newId = getSessionIdProvider().newId();

        LOGGER.debug("New id {}", newId);

        if (appendTimeStamp()) {
            LOGGER.debug("Appending current timeStamp on the session {}", timestamp);
            return new StringBuffer()
                    .append(newId)
                    .append(SESSION_ID_TIMESTAMP_SEPARATOR)
                    .append(timestamp)
                    .toString();
        }

        return newId;
    }

    @Override
    public String encodeUrl(String sessionId, String url) {
        LOGGER.debug("For cookie tracking, no need to encode the URL {}", url);
        return url;
    }

    @Override
    public void propagateSession(String sessionId, HttpServletResponse response) {
        LOGGER.debug("For URL tracking propagation logic is not required.");
    }

    protected String clean(String value) {
        LOGGER.debug("Cleaning up the session ID {}", value);

        if (!appendTimeStamp()) {
            LOGGER.debug("Timestamp was not appended. So returning the session ID.");
            return getSessionIdProvider().readId(value);
        }

        int separatorIndex = StringUtils.lastIndexOf(value, SESSION_ID_TIMESTAMP_SEPARATOR);
        String timeStamp = (separatorIndex != -1) ? StringUtils.substring(value, separatorIndex) : StringUtils.EMPTY;
        String cleanValue = (separatorIndex != -1) ? StringUtils.substring(value, 0, separatorIndex) : value;

        LOGGER.debug("Cleaned up value {} and timestamp {}", cleanValue, timeStamp);

        cleanValue = getSessionIdProvider().readId(cleanValue);
        return cleanValue != null ? cleanValue + timeStamp : cleanValue;
    }

    private SessionIdProvider getSessionIdProvider() {
        return Optional.ofNullable(sessionIdProviders.get(getProviderName()))
                .orElseThrow(() -> new IllegalArgumentException("No Session ID Provider with name " + getProviderName() + " is configured."));
    }

    protected abstract boolean appendTimeStamp();
    protected abstract String getProviderName();
}
