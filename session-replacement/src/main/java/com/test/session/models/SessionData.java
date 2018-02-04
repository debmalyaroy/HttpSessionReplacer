package com.test.session.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Container for basic session information. It includes the following standard
 * data uses for <code>HttpSession</code> in JEE:
 * <ul>
 * <li>creation time as timestamp</li>
 * <li>last accesses time as timestamp</li>
 * <li>max inactive interval in seconds</li>
 * <li>whether the session is a new one</li>
 * </ul>
 *
 * It also includes additional information:
 * <ul>
 * <li>last accessed time at moment of the retrieval from repository. This can
 * be used to manage expiration strategy - e.g. as we now have new access time,
 * we can clean timers that are based on this old session accessed time.</li>
 * </ul>
 *
 */
public class SessionData {
    private final String id;
    private final long creationTime = System.currentTimeMillis();
    private int maxInactiveInterval;
    private long lastAccessedTime;
    private boolean isNew;
    private final Map<String, SessionAttribute> sessionAttributes = new ConcurrentHashMap<>();

    public SessionData(String id, long lastAccessedTime, int maxInactiveInterval) {
        this.id = id;
        this.maxInactiveInterval = maxInactiveInterval;
        this.lastAccessedTime = lastAccessedTime;
    }

    public SessionData(String sessionId, int maxInactiveInterval, boolean isNew) {
        this(sessionId, System.currentTimeMillis(), maxInactiveInterval);
        this.isNew = isNew;
    }

    public SessionData(String sessionId, int maxInactiveInterval) {
        this.id = sessionId;
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public String getId() {
        return id;
    }

    public Map<String, SessionAttribute> getSessionAttributes() {
        return sessionAttributes;
    }

    public SessionAttribute getAttribute(String attributeName) {
        return sessionAttributes.get(attributeName);
    }

    public void setAttribute(String attributeName, SessionAttribute value) {
        if (sessionAttributes.containsKey(attributeName)) {
            sessionAttributes.replace(attributeName, value);
        } else {
            sessionAttributes.put(attributeName, value);
        }
    }

    public void removeAttribute(String attributeName) {
        sessionAttributes.remove(attributeName);
    }

    @Override
    public String toString() {
        return String.format(
                "SessionData [creationTime=%s, id=%s, lastAccessedTime=%s, maxInactiveInterval=%s, isNew=%s, sessionAttributes=%s]",
                creationTime, id, lastAccessedTime, maxInactiveInterval, isNew, sessionAttributes);
    }
}
