package com.test.session.servlet;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;

import com.test.session.models.SessionData;

/**
 * This class implements {@link HttpSession} backed by
 * {@link RepositoryBackedSession}.
 * <p>
 * When there are concurrent uses of the HttpSession in multiple requests,
 * {@link RepositoryBackedHttpSessionWrapper} is used as facade to primary
 * instance.
 */
@SuppressWarnings("deprecation")
public final class RepositoryBackedHttpSession extends RepositoryBackedSession implements HttpSession {
    private static final String[] BLANK_STRING_ARRAY = {};
    private final ServletContext servletContext;

    public RepositoryBackedHttpSession(ServletContext servletContext, SessionData sessionData, boolean replicateOnGet, String configuredRepositoryFactory) {
        super(sessionData, replicateOnGet, configuredRepositoryFactory);
        this.servletContext = servletContext;
    }

    public RepositoryBackedHttpSession(RepositoryBackedHttpSession wrapped) {
        super(wrapped);
        this.servletContext = wrapped.servletContext;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException("getSessionContext() is deprecated");
    }

    @Override
    public void putValue(String key, Object value) {
        setAttribute(key, value);
    }

    @Override
    public void removeValue(String key) {
        removeAttribute(key);
    }

    @Override
    public Object getValue(String key) {
        return getAttribute(key);
    }

    @Override
    public String[] getValueNames() {
        return Collections.list(getAttributeNames()).stream().collect(Collectors.toList()).toArray(BLANK_STRING_ARRAY);
    }

    @Override
    public void setMaxInactiveInterval(int maxInactiveInterval) {
        sessionData.setMaxInactiveInterval(maxInactiveInterval);
    }

    @Override
    public Object getAttribute(String key) {
        LOGGER.debug("Getting the stored attribute using key {}", key);
        assertValid();

        Attribute attr = retrieveAttribute(key, attrs.get(key));
        LOGGER.debug("Attribute retrieved {}", attr);

        if (attr == null || attr.deleted) {
            LOGGER.debug("Attribute is either null or set to be deleted. So returning null.");
            return null;
        }

        // If we do get on non simple type, and we have replicate on get, 
        // we should replicate the attribute.
        if (replicateOnGet(attr.value)) {
            LOGGER.debug("Setting attribute for persisting.");

            attr.changed = true;
            dirty = true;
            checkUsedAndLock();
        }

        LOGGER.debug("Returning attribute {}", attr);
        return attr.value;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        assertValid();

        List<String> keys = attrs.entrySet().stream()
                .filter(map -> map.getValue().value != null)
                .map(map -> map.getKey())
                .collect(Collectors.toList());

        // If key isn't already in local cache, add it to enumeration.
        getAllRepositoryKeys().stream().filter(key -> !attrs.containsKey(key)).forEach(key -> keys.add(key));

        LOGGER.debug("All attribute names with not null value {}", keys);
        return Collections.enumeration(keys);
    }

    @Override
    public long getCreationTime() {
        assertValid();
        return sessionData.getCreationTime();
    }

    @Override
    public String getId() {
        assertValid();
        return sessionData.getId();
    }

    @Override
    public long getLastAccessedTime() {
        assertValid();
        return sessionData.getLastAccessedTime();
    }

    @Override
    public int getMaxInactiveInterval() {
        assertValid();
        return sessionData.getMaxInactiveInterval();
    }

    @Override
    public void invalidate() {
        assertValid();
        doInvalidate(false);
    }

    @Override
    public boolean isNew() {
        assertValid();
        return sessionData.isNew();
    }

    @Override
    public void removeAttribute(String name) {
        LOGGER.debug("Removing attribute with name {}", name);
        assertValid();

        Attribute attr = attrs.get(name);
        LOGGER.debug("Returned attribute from cache {}", attr);

        if (attr == null) {
            LOGGER.debug("No data present in cache. Retrieving from repository.");
            attr = retrieveAttribute(name, null);

            if (attr == null) {
                LOGGER.debug("Attribute not found in repository.");
                return;
            }
        }

        LOGGER.debug("Removing the attribute from repository.");
        removeAttributeFromRepository(name);

        LOGGER.debug("Marking the attribute for delete in cache.");

        attr.value = null;
        attr.deleted = true;
        attr.changed = true;
        dirty = true;
        checkUsedAndLock();

        LOGGER.debug("Attribute to be deleted {}", attr);
    }

    @Override
    public void setAttribute(String key, Object value) {
        LOGGER.debug("Setting attribute with name {} with value {}", key, value);
        assertValid();

        if (value == null) {
            LOGGER.debug("Value is passed as null. So the attribute is sent to remove.");
            removeAttribute(key);
            return;
        }

        Attribute attr;

        if (sessionData.isNonCacheable(key)) {
            LOGGER.debug("Attribute is non cachable. So Storing it in repository and getting it from the map.");

            setSessionAttribute(key, value);
            attr = attrs.get(key);
        } else {
            LOGGER.debug("Attribute is cachable. So finding if any attribute present in cache.");
            attr = retrieveAttribute(key, attrs.get(key));
        }

        if (attr == null) {
            LOGGER.debug("No attribute found. Creating one and setting it in the map.");

            attr = new Attribute(value);
            attrs.put(key, attr);
        } else {
            LOGGER.debug("Attribute found. So updating the value and setting delete flag as off.");

            attr.value = value;
            attr.deleted = false;
        }

        LOGGER.debug("Attribute is changed on session. So the changed and dirty flags are set to true.");

        attr.changed = true;
        dirty = true;

        checkUsedAndLock();
        LOGGER.debug("Attribute to set {}", attr);
    }

    @Override
    public String toString() {
        return String.format("RepositoryBackedHttpSession [%s]", sessionData);
    }
}
