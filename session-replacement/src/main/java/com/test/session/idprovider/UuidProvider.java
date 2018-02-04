package com.test.session.idprovider;

import java.util.UUID;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionIdProvider;
import com.test.session.models.SessionConstants;

/**
 * This class generates session id based on UUID.
 */
@Component(immediate = true, name = SessionConstants.UUID_PROVIDER_VALUE)
@Service
public class UuidProvider implements SessionIdProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(UuidProvider.class);

    @Override
    public String newId() {
        LOGGER.debug("Creating new ID.");
        return UUID.randomUUID().toString();
    }

    @Override
    public String readId(String value) {
        LOGGER.debug("Reading ID {} and converting it to UUID.", value);

        try {
            return UUID.fromString(value).toString();
        } catch (Exception e) { // NOSONAR If exception it is not valid UUID
            LOGGER.info("Cookie value vas not a valid UUID: {}", value);
            return null;
        }
    }
}
