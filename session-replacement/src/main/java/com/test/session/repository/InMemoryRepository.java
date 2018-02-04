package com.test.session.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpSession;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.TaskExecutorProcess;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionManager;
import com.test.session.api.SessionRepository;
import com.test.session.models.RedisConstants;
import com.test.session.models.SessionConstants;
import com.test.session.models.SessionData;

/**
 * Session Repository implementation that stores session in memory. This class
 * can be used for testing and development purposes. This repository will also
 * be used for the web apps marked as non-distributable when distribution of
 * sessions is not enforced.
 * <p>
 * We are storing session in memory in {@link ConcurrentHashMap}. For each
 * session we store {@link SessionData} and session attributes. Multiple threads
 * can access both separate session and same session id, and while repository is
 * thread safe, functional concurrency when using same session id from different
 * threads must be assured in application code (as is general case for
 * {@link HttpSession}.
 * </p>
 * <p>
 * Sessions are cleaned-up by a special task running in separate thread every 60
 * seconds. Only one task is running at the given time and this is assured by
 * {@link SessionManager#schedule(String, Runnable, long)} method.
 * </p>
 */
@Component(immediate = true, name = SessionConstants.IN_MEMORY_REPOSITORY_VALUE)
@Service
public class InMemoryRepository implements SessionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryRepository.class);

    private final Map<String, SessionData> sessionDataCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> sessionAttributeCache = new ConcurrentHashMap<>();

    @Reference
    private SessionManager sessionManager;

    @Reference(bind = "bindSessionConfigurationService")
    private SessionConfigurationService sessionConfiguration;

    private ScheduledFuture<?> cleanupFuture;

    protected void bindSessionConfigurationService(final SessionConfigurationService service, Map<String, ?> properties) {
        LOGGER.debug("Binding session configuration service.");
        this.sessionConfiguration = service;
        long interval = RedisConstants.ONE_MINUTE;

        // We are scheduling task here to cleanup expired sessions
        // Note that this will go through all the sessions, so the performance
        // may suffer when there is a large number of sessions to go through.
        LOGGER.debug("Schedule the cleanup task to run every minute.");
        cleanupFuture = (ScheduledFuture<?>) TaskExecutorProcess.getInstance(sessionConfiguration).submit(createCleanupTask(), true, interval, interval, TimeUnit.SECONDS);
    }

    @Deactivate
    protected void close(Map<String, ?> properties) {
        LOGGER.debug("Closing the bundle. Stopping the scheduled task.");

        if (cleanupFuture != null) {
            cleanupFuture.cancel(true);
            cleanupFuture = null;
        }
    }

    @Override
    public SessionData getSessionData(String id) {
        LOGGER.debug("Getting the session data associated with the session ID {}", id);
        return sessionDataCache.get(id(id));
    }

    @Override
    public void storeSessionData(SessionData sessionData) {
        LOGGER.debug("Getting the session data {} in repository.", sessionData);
        String id = id(sessionData.getId());

        sessionDataCache.put(id, sessionData);
        sessionAttributeCache.putIfAbsent(id, new ConcurrentHashMap<String, Object>());
    }

    @Override
    public Set<String> getAllKeys(SessionData session) {
        LOGGER.debug("Getting all keys associated with the session {}", session);
        return Optional.ofNullable(sessionAttributeCache.get(id(session.getId())))
                .map(attributes -> attributes.keySet())
                .orElse(Collections.emptySet());
    }

    @Override
    public Object getSessionAttribute(SessionData session, String attribute) {
        LOGGER.debug("Getting the session attribute with key {} for sesison {}", attribute, session);
        return Optional.ofNullable(sessionAttributeCache.get(id(session.getId())))
                .map(attributes -> attributes.get(attribute))
                .orElse(null);
    }

    @Override
    public void remove(SessionData session) {
        LOGGER.debug("Remove the session data from repository {}", session);
        remove(session.getId());
    }

    @Override
    public boolean prepareRemove(SessionData session) {
        LOGGER.debug("Remove the session data from repository {}", session);
        sessionDataCache.remove(id(session.getId()));
        return true;
    }

    @Override
    public CommitTransaction startCommit(SessionData session) {
        return new InMemoryTransaction(session);
    }

    @Override
    public void requestFinished() {
        LOGGER.debug("No cleanup is necessary.");
    }

    @Override
    public void setSessionAttribute(SessionData session, String name, Object value) {
        getAttributeMap(session.getId()).put(name, value);
    }

    @Override
    public void removeSessionAttribute(SessionData session, String name) {
        getAttributeMap(session.getId()).remove(name);
    }

    @Override
    public Collection<String> getOwnedSessionIds() {
        LOGGER.debug("Getting all session IDs.");
        return Collections.unmodifiableCollection(sessionDataCache.values().stream()
                                                    .map(sd -> sd.getId())
                                                    .collect(Collectors.toList()));
    }

    @Override
    public void sessionIdChange(SessionData sessionData) {
        LOGGER.debug("This functionality is for Servlet 3.1 container and not yet implemented.");
    }

    private Map<String, Object> getAttributeMap(String sessionId) {
        String id = id(sessionId);
        Map<String, Object> attrs = sessionAttributeCache.get(id);

        if (attrs == null) {
            attrs = new ConcurrentHashMap<>();
            Map<String, Object> attrPrev = sessionAttributeCache.putIfAbsent(id, attrs);

            if (attrPrev != null) {
                attrs = attrPrev;
            }
        }

        return attrs;
    }

    private void remove(String sessionId) {
        String id = id(sessionId);

        sessionDataCache.remove(id);
        sessionAttributeCache.remove(id);
    }

    private String id(String id) {
        return new StringBuffer()
                .append(sessionConfiguration.getNamespace())
                .append(':')
                .append(id)
                .toString();
    }

    /**
     * Cleanup task removes expired sessions from memory store.
     */
    private Runnable createCleanupTask() {
        return () -> {
            long instant = System.currentTimeMillis();
            LOGGER.debug("Cleanup task started at {}", instant);

            try {
                // Find all session which are already expired.
                sessionDataCache.values().stream()
                    .filter(sd -> sd != null)
                    .filter(sd -> (instant - sd.getLastAccessedTime()) > TimeUnit.SECONDS.toMillis(sd.getMaxInactiveInterval()))
                    .forEach(sd -> {
                        LOGGER.debug("Expiring session {}", sd);

                        sessionManager.delete(sd, true);
                        remove(sd.getId());
                    });
            } catch (Exception e) {
                LOGGER.error("An error occured while trying to exipre sessions", e);
            }
        };
    }

    /**
     * The {@link SessionRepository.CommitTransaction} implementation that
     * stores all changed and removes all removed attributes into the
     * {@link InMemoryRepository} story.
     */
    private final class InMemoryTransaction implements CommitTransaction {
        private ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
        private Map<String, Object> toRemove = new ConcurrentHashMap<>();
        private final SessionData session;

        private InMemoryTransaction(SessionData session) {
            this.session = session;
        }

        @Override
        public void commit() {
            String id = id(session.getId());
            SessionData sessionData = sessionDataCache.get(id);

            if (sessionData == null) {
                sessionData = new SessionData(session.getId(), session.getLastAccessedTime(), session.getMaxInactiveInterval(), session.getCreationTime());
            }

            sessionData.setLastAccessedTime(session.getLastAccessedTime());
            sessionData.setMaxInactiveInterval(session.getMaxInactiveInterval());

            sessionDataCache.put(id, sessionData);

            Map<String, Object> attrs = getAttributeMap(session.getId());
            attrs.putAll(attributes);

            for (String key : toRemove.keySet()) {
                attrs.remove(key);
            }
        }

        @Override
        public void changeAttribute(String key, Object value) {
            if (value == null) {
                toRemove.put(key, key);
            } else {
                attributes.put(key, value);
            }
        }
    }
}