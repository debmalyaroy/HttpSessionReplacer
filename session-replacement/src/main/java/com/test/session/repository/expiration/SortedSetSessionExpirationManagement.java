package com.test.session.repository.expiration;

import static com.test.session.models.RedisConstants.SORTED_SET_STRATEGY_VALUE;
import static redis.clients.util.SafeEncoder.encode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.TaskExecutorProcess;
import com.test.session.api.OSGiDependencyService;
import com.test.session.api.RedisExpirationStrategy;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionManager;
import com.test.session.models.RedisConstants;
import com.test.session.models.SessionData;
import com.test.session.repository.RedisSessionRepository;

/**
 * A strategy for expiring session instances based on Redis Sorted Set (ZRANGE).
 * <p>
 * In this strategy, two keys are used for each session. Main key contains
 * session data and is managed by {@link RedisSessionRepository}. The second key
 * is the sorted set key where all sessions are stored using expiration
 * expiration time as the score.
 * <p>
 * A task is run periodically (every second) and retrieves all sessions that
 * have expired up to the moment.
 * <p>
 * Following risks are possible:
 * <ul>
 * <li>For long running requests, session may expire before request completes. A
 * node not operating the session can then remove session data from Redis. To
 * mitigate the issue application can call various methods that effectively
 * trigger session 'touch' thus pushing the expiry date further in the future.
 * <li>Two nodes may initiate session expire at similar times and may request
 * delete of same session. Delete process should implement logic that performs
 * session delete atomically.
 * <ul>
 */
@Component(immediate = true, name = SORTED_SET_STRATEGY_VALUE)
@Service
public class SortedSetSessionExpirationManagement implements RedisExpirationStrategy {
    private static Logger LOGGER = LoggerFactory.getLogger(SortedSetSessionExpirationManagement.class.getName());
    private static final Long ONE = 1L;

    @Reference
    private OSGiDependencyService dependencyService;

    @Reference(bind = "bindSessionConfigurationService")
    private SessionConfigurationService sessionConfigurationService;

    @Reference
    private SessionManager sessionManager;

    private ScheduledFuture<?> cleanupFuture;

    protected void bindSessionConfigurationService(final SessionConfigurationService service, Map<String, ?> properties) {
        this.sessionConfigurationService = service;
        startExpiredSessionsTask();
    }

    @Deactivate
    protected void close() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(true);
            cleanupFuture = null;
        }
    }

    @Override
    public void sessionIdChange(SessionData sessionData) {
        byte[] sessionToExpireKey = getSessionToExpiryKey();

        dependencyService.getRedisConnector().zrem(sessionToExpireKey, sortedSetElem(sessionData.getOldSessionId()));
        dependencyService.getRedisConnector().zadd(sessionToExpireKey, sessionData.expiresAt(), sortedSetElem(sessionData.getId()));
    }

    @Override
    public void sessionDeleted(SessionData session) {
        String sessionId = session.getId();

        if (sessionConfigurationService.isSticky() && session.getPreviousOwner() != null && !sessionConfigurationService.getNode().equals(session.getPreviousOwner())) {
            sessionId = sessionId.concat(":").concat(session.getPreviousOwner());
        }

        dependencyService.getRedisConnector().zrem(getSessionToExpiryKey(), sortedSetElem(sessionId));
    }

    @Override
    public void sessionTouched(SessionData session) {
        byte[] sessionToExpireKey = getSessionToExpiryKey();
        byte[] sessionKey = sessionKey(session.getId());
        int sessionExpireInSeconds = session.getMaxInactiveInterval();

        // If session doesn't expire, then remove expire key and persist session
        if (sessionExpireInSeconds <= 0) {
            dependencyService.getRedisConnector().persist(sessionKey);
            dependencyService.getRedisConnector().zadd(sessionToExpireKey, Double.MAX_VALUE, sortedSetElem(session.getId()));
        } else {
            // If session expires, then add session key to expirations cleanup instant, 
            // set expire on session and set expire on session expiration key
            dependencyService.getRedisConnector().zadd(sessionToExpireKey, session.expiresAt(), sortedSetElem(session.getId()));
            dependencyService.getRedisConnector().expire(sessionKey, sessionExpireInSeconds + RedisConstants.SESSION_PERSISTENCE_SAFETY_MARGIN);
        }
    }

    private byte[] sortedSetElem(String id) {
        if (sessionConfigurationService.isSticky()) {
            id = id.concat(":").concat(sessionConfigurationService.getNode());
        }

        return encode(id);
    }

    private boolean sessionOwned(byte[] session) {
        byte[] ownerAsBytes = encode(sessionConfigurationService.getNode());

        if (!sessionConfigurationService.isSticky()) {
            return false;
        }

        if (session.length < ownerAsBytes.length + 1) {
            return false;
        }

        for (int i = ownerAsBytes.length - 1, j = session.length - 1; i >= 0; i--, j--) {
            if (session[j] != ownerAsBytes[i]) {
                return false;
            }
        }

        if (session[session.length - ownerAsBytes.length - 1] != ':') {
            return false;
        }

        return true;
    }

    private void expireSessions(long start, long end, boolean forceExpire, SessionManager sessionManager) {
        if (sessionManager == null) {
            return;
        }

        byte[] sessionToExpireKey = getSessionToExpiryKey();
        Set<byte[]> sessionsToExpire = dependencyService.getRedisConnector().zrangeByScore(sessionToExpireKey, start, end);

        if (CollectionUtils.isNotEmpty(sessionsToExpire)) {
            sessionsToExpire
                .stream()
                .filter(session -> (forceExpire || sessionOwned(session)) && ONE.equals(dependencyService.getRedisConnector().zrem(sessionToExpireKey, session)))
                .forEach(session -> {
                    String sessionId = extractSessionId(session);

                    LOGGER.debug("Starting cleanup of session '{}'", sessionId);
                    // Find the session data and pass
                    // sessionManager.delete(sessionId, true);
            });
        }
    }

    private String extractSessionId(byte[] session) {
        if (sessionConfigurationService.isSticky()) {
            for (int i = 0; i < session.length; i++) {
                if (session[i] == ':') {
                    return null; //encode(session, 0, i);
                }
            }

            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Unable to retrieve session id from expire key {}", encode(session));
            }
            // Missing session owner, assume whole array is session id
        }

        return encode(session);
    }

    private byte[] getSessionToExpiryKey() {
        return encode(RedisConstants.ALLSESSIONS_KEY + sessionConfigurationService.getNamespace());
    }

    private byte[] sessionKey(String sessionId) {
        return encode(new StringBuffer()
                        .append(RedisConstants.DEFAULT_SESSION_PREFIX)
                        .append(":")
                        .append(sessionConfigurationService.getNamespace())
                        .append(":")
                        .append("{")
                        .append(sessionId)
                        .append('}')
                        .toString());
    }

    private void startExpiredSessionsTask() {
        boolean sticky = sessionConfigurationService.isSticky();

        // Task to perform session expiration. It will retrieve ZRANGEBYSCORE with values up to now instant, 
        // and then for each retrieved key, it will expire it.
        Runnable cleanupTask = () -> {
            if (sessionManager == null) {
                return;
            }

            long now = System.currentTimeMillis();
            long start = sticky ? now - RedisConstants.SESSION_PERSISTENCE_SAFETY_MARGIN_MILLIS : 0;

            LOGGER.debug("Cleaning up sessions expiring at {}", now);
            expireSessions(start, now, !sticky, sessionManager);

            if (sticky) {
                expireSessions(0, start, true, sessionManager);
            }
        };

        // Interval of polling is either 1/10th of the maximum inactive interval, or
        // every REGULAR_CLEANUP_INTERVAL (10) seconds, whichever is smaller
        long interval = Math.min((sessionConfigurationService.getMaxInactiveInterval() / 10) + 1, RedisConstants.REGULAR_CLEANUP_INTERVAL);

        if (interval <= 0) {
            interval = RedisConstants.REGULAR_CLEANUP_INTERVAL;
        }

        LOGGER.debug("Cleanup interval for sessions is {}", interval);
        cleanupFuture = (ScheduledFuture<?>) TaskExecutorProcess.getInstance(sessionConfigurationService).submit(cleanupTask, true, interval, interval, TimeUnit.SECONDS);
    }
}