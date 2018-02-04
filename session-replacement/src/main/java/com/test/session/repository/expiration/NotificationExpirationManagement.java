package com.test.session.repository.expiration;

import static com.test.session.models.RedisConstants.NOTIFICATION_STRATEGY_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static redis.clients.util.SafeEncoder.encode;

import java.util.Arrays;
import java.util.Calendar;
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
import com.test.session.connection.api.RedisConnector;
import com.test.session.connection.api.RedisConnector.ResponseFacade;
import com.test.session.connection.api.RedisConnector.TransactionRunner;
import com.test.session.models.RedisConstants;
import com.test.session.models.SessionData;

/**
 * A strategy for expiring session instances. This performs several operations:
 *
 * Several keys are used for each session. Main key contains session data.
 * Another key is created and used only for session expire event. The session
 * expire event will trigger the cleanup of the session. The third key is using
 * minutes as key index and contains a set of indexes of all sessions that
 * expire in the minute preceding the minute specified by index key. Following
 * is the discussion of the algorithm sourced from Spring Session project.
 *
 * Redis has no guarantees of when an expired session event will be fired. In
 * order to ensure expired session events are processed in a timely fashion the
 * expiration (rounded to the nearest minute) is mapped to all the sessions that
 * expire at that time. Whenever {@link #cleanExpiredSessions()} is invoked, the
 * sessions for the previous minute are then accessed to ensure they are deleted
 * if expired. All nodes are running a task that does this monitoring, but only
 * the first node that polls Redis for a given key will be responsible for
 * session expiration as we SMEMBERS and DEL on the key within single Redis
 * transaction.
 *
 * In some instances the {@link #cleanExpiredSessions()} method may not be not
 * invoked for a specific time. For example, this may happen when a server is
 * restarted. To account for this, the expiration on the Redis session is also
 * set. For example, if none of the nodes was active during minute following
 * session expiration, the check will not be done, and sessions will silently
 * expire.
 *
 * In stickiness scenario only the node owning the session will delete session
 * on expire event. When using node stickiness, the expire keys contain also
 * node identifier. When listener receives event that this key expires, it
 * checks if key prefix matches the node's one. If it is the case, the session
 * will be deleted. We need to handle also the case when the owner node doesn't
 * receive this notification (e.g. the node is down, there was network issue,
 * Redis servers are busy). For this reason we add `forced-expirations` key that
 * is set one minute after the `expirations` key. It has almost same semantics
 * and logic, with the only difference being that the key is different and it is
 * set to expire one minute later.
 */
@Component(immediate = true, name = NOTIFICATION_STRATEGY_VALUE)
@Service
public class NotificationExpirationManagement implements RedisExpirationStrategy {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationExpirationManagement.class);

    @Reference
    private OSGiDependencyService dependencyService;

    @Reference(bind = "bindSessionConfigurationService")
    private SessionConfigurationService sessionConfigurationService;

    @Reference
    private SessionManager sessionManager;

    private ExpirationListener expirationListener;
    private ScheduledFuture<?> cleanupFuture;
    private ScheduledFuture<?> forceCleanupFuture;

    protected void bindSessionConfigurationService(final SessionConfigurationService service, Map<String, ?> properties) {
        this.sessionConfigurationService = service;
        startExpiredSessionsTask();
    }

    @Deactivate
    protected void close(Map<String, ?> properties) {
        if (expirationListener != null) {
            expirationListener.close(dependencyService.getRedisConnector());
            expirationListener = null;
        }

        if (cleanupFuture != null) {
            cleanupFuture.cancel(true);
            cleanupFuture = null;
        }

        if (forceCleanupFuture != null) {
            forceCleanupFuture.cancel(true);
            forceCleanupFuture = null;
        }
    }

    @Override
    public void sessionDeleted(SessionData session) {
        RedisConnector redis = dependencyService.getRedisConnector();

        byte[] expireKey = getExpirationsKey(roundUpToNextMinute(session.expiresAt()));
        redis.srem(expireKey, sessionKey(session.getId()));

        byte[] sessionExpireKey = getSessionExpireKey(session.getId());
        redis.del(sessionExpireKey);
    }

    @Override
    public void sessionTouched(SessionData session) {
        new ExpirationManagement().manageExpiration(session);
    }

    @Override
    public void sessionIdChange(SessionData session) {
        RedisConnector redis = dependencyService.getRedisConnector();

        redis.rename(getSessionExpireKey(session.getOldSessionId()), getSessionExpireKey(session.getId()));

        // Update clean-up sets
        long expireCleanupInstant = roundUpToNextMinute(session.expiresAt());
        byte[] expirationsKey = getExpirationsKey(expireCleanupInstant);
        byte[] sessionKey = sessionKey(session.getId());
        byte[] oldSessionKey = sessionKey(session.getOldSessionId());

        redis.srem(expirationsKey, oldSessionKey);
        redis.sadd(expirationsKey, sessionKey);

        if (sessionConfigurationService.isSticky()) {
            long forceCleanupInstant = roundUpToNextMinute(expireCleanupInstant);
            byte[] forceExpirationsKey = getForcedExpirationsKey(forceCleanupInstant);

            redis.srem(forceExpirationsKey, oldSessionKey);
            redis.sadd(forceExpirationsKey, sessionKey);
        }

    }

    private String constructKeyExpirePrefix(String sessionOwner) {
        return RedisConstants.DEFAULT_SESSION_EXPIRE_PREFIX + ":" + sessionOwner + ":" + sessionConfigurationService.getNode() + ":";
    }

    private Set<byte[]> getKeysToExpire(byte[] key) {
        RedisConnector redis = dependencyService.getRedisConnector();

        // In Redis 3.2 we use SPOP to get bulk of keys to expire
        if (!redis.supportsMultiSpop()) {
            return redis.transaction(key, smembersAndDel(key)).get();
        } else {
            Set<byte[]> res = redis.spop(key, RedisConstants.SPOP_BULK_SIZE);
            if (res == null || res.isEmpty() || res.size() < RedisConstants.SPOP_BULK_SIZE) {
                redis.del(key);
            }

            return res;
        }
    }

    private String getKeyPrefix() {
        return RedisConstants.DEFAULT_SESSION_PREFIX + ":" + sessionConfigurationService.getNamespace() + ":";
    }

    private byte[] getSessionExpireKey(String id) {
        String keyExpirePrefix = sessionConfigurationService.isSticky() ? constructKeyExpirePrefix(sessionConfigurationService.getNode()) : getKeyPrefix();

        return encode(new StringBuffer()
                        .append(keyExpirePrefix)
                        .append('{')
                        .append(id)
                        .append('}')
                        .toString());
    }

    private byte[] getSessionExpireKey(String owner, String id) {
        String ownerBasedPrefix = constructKeyExpirePrefix(owner);

        return encode(new StringBuffer()
                        .append(ownerBasedPrefix)
                        .append('{')
                        .append(id)
                        .append('}')
                        .toString());
    }

    private byte[] getExpirationsKey(long instant) {
        String exp = Long.toString(instant);
        String expirationsPrefix = getKeyPrefix().concat("expirations:");

        return encode(new StringBuffer()
                        .append(expirationsPrefix)
                        .append(exp)
                        .toString());
    }

    private byte[] getForcedExpirationsKey(long instant) {
        String forcedExpirationsPrefix = sessionConfigurationService.isSticky() ? getKeyPrefix().concat("forced-expirations:") : null;
        String exp = Long.toString(instant);

        return encode(new StringBuffer()
                        .append(forcedExpirationsPrefix)
                        .append(exp)
                        .toString());
    }

    private void startExpiredSessionsTask() {
        TaskExecutorProcess.getInstance(sessionConfigurationService).submit(new SubscriptionRunner(), false, 0, 0, null);

        long interval = RedisConstants.ONE_MINUTE;

        /**
         * This checks if there are any sessions which should have expired in
         * previous minutes, but for which there were no processed notification. If
         * such sessions are found, the expiration notification is triggered using
         * EXISTS command.
         */
        cleanupFuture = (ScheduledFuture<?>) TaskExecutorProcess.getInstance(sessionConfigurationService).submit(createTriggerExpirationTask(), true, interval, interval, TimeUnit.SECONDS);

        if (sessionConfigurationService.isSticky()) {
            // When we have sticky sessions, we perform also second pass to
            // capture sessions that were not cleaned by the node that last accessed them
            forceCleanupFuture = (ScheduledFuture<?>) TaskExecutorProcess.getInstance(sessionConfigurationService).submit(createCleanHangingSessionsTask(), true, interval, interval, TimeUnit.SECONDS);
        }
    }

    private static long roundUpToNextMinute(long timeInMs) {
        Calendar date = Calendar.getInstance();

        date.setTimeInMillis(timeInMs);
        date.add(Calendar.MINUTE, 1);
        date.clear(Calendar.SECOND);
        date.clear(Calendar.MILLISECOND);

        return date.getTimeInMillis();
    }

    private static long roundDownMinute(long timeInMs) {
        Calendar date = Calendar.getInstance();

        date.setTimeInMillis(timeInMs);
        date.clear(Calendar.SECOND);
        date.clear(Calendar.MILLISECOND);

        return date.getTimeInMillis();
    }

    private static TransactionRunner<Set<byte[]>> smembersAndDel(final byte[] key) {
        return (transaction) -> {
                ResponseFacade<Set<byte[]>> result = transaction.smembers(key);
                transaction.del(key);
                return result;
        };
    }

    private Runnable  createCleanHangingSessionsTask() {
        return () -> {
            if (sessionManager == null || dependencyService.getRedisConnector() == null) {
                return;
            }

            long prevMin = roundDownMinute(System.currentTimeMillis());
            LOGGER.debug("Cleaning up sessions expiring at {}", prevMin);

            byte[] key = getForcedExpirationsKey(prevMin);
            Set<byte[]> sessionsToExpire = getKeysToExpire(key);

            if (CollectionUtils.isEmpty(sessionsToExpire)) {
                return;
            }

            sessionsToExpire.forEach(session -> {
                LOGGER.debug("Cleaning-up session {}", new String(session));

                // check if session is active
                if (dependencyService.getRedisConnector().exists(getSessionKey(session))) {
                    // We run session delete in another thread, otherwise we
                    // would block this thread listener.
                    // TODO: Find the session data and pass
                    // sessionManager.deleteAsync(encode(session), true);
                }
            });
        };
    }

    private Runnable createTriggerExpirationTask() {
        return () -> {
            if (dependencyService.getRedisConnector() == null) {
                return;
            }

            long prevMin = roundDownMinute(System.currentTimeMillis());
            LOGGER.debug("Triggering up sessions expiring at {}", prevMin);

            byte[] key = getExpirationsKey(prevMin);
            Set<byte[]> sessionsToExpire = getKeysToExpire(key);

            if (CollectionUtils.isEmpty(sessionsToExpire)) {
                return;
            }

            sessionsToExpire.forEach(session -> {
                LOGGER.debug("Expiring session {}", new String(session));

                byte[] sessionExpireKey = getSessionExpireKey(encode(session));
                dependencyService.getRedisConnector().exists(sessionExpireKey);
            });
        };
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

    private byte[] getSessionKey(byte[] session) {
        byte[] keyPrefixByteArray = encode(getKeyPrefix());
        int prefixLength = keyPrefixByteArray.length;
        byte[] copy = Arrays.copyOf(keyPrefixByteArray, prefixLength + session.length + 1);

        for (int i = 0; i < session.length; i++) {
            copy[prefixLength + i] = session[i];
        }

        copy[prefixLength + session.length] = '}';
        return copy;
    }

    /**
     * Helper class that implements expiration logic
     */
    final class ExpirationManagement {
        RedisConnector redis = dependencyService.getRedisConnector();
        private long expireCleanupInstant;
        private byte[] sessionKey;
        private int sessionExpireInSeconds;
        private byte[] expirationsKey;
        long forceCleanupInstant;
        byte[] forceExpirationsKey;

        void manageExpiration(SessionData session) {
            prepareKeys(session);
            manageCleanupKeys(session);
            manageSessionFailover(session);
            byte[] sessionExpireKey = getSessionExpireKey(session.getId());

            // If session doesn't expire, then remove expire key and persist
            // session
            if (sessionExpireInSeconds <= 0) {
                redis.del(sessionExpireKey);
                redis.persist(sessionKey);
            } else {
                // If session expires, then add session key to expirations
                // cleanup
                // instant, set expire on
                // session and set expire on session expiration key
                redis.sadd(expirationsKey, sessionKey);
                redis.expireAt(expirationsKey,
                        MILLISECONDS.toSeconds(expireCleanupInstant) + RedisConstants.SESSION_PERSISTENCE_SAFETY_MARGIN);
                if (sessionConfigurationService.isSticky()) {
                    redis.sadd(forceExpirationsKey, sessionKey);
                    redis.expireAt(forceExpirationsKey,
                            MILLISECONDS.toSeconds(forceCleanupInstant) + RedisConstants.SESSION_PERSISTENCE_SAFETY_MARGIN);
                }
                redis.setex(sessionExpireKey, sessionExpireInSeconds, RedisConstants.EMPTY_STRING);
                redis.expire(sessionKey, sessionExpireInSeconds + RedisConstants.SESSION_PERSISTENCE_SAFETY_MARGIN);
            }
        }

        private void manageSessionFailover(SessionData session) {
            // If stickiness is active, and there was failover, we need to
            // delete
            // previous session expire key
            if (sessionConfigurationService.isSticky() && !sessionConfigurationService.getNode().equals(session.getPreviousOwner())) {
                redis.del(getSessionExpireKey(session.getPreviousOwner(), session.getId()));
            }
        }

        /**
         * Sets up all keys used during expiration management. Those are session
         * key, key for session cleanup and optionally clean for forced session
         * cleanup when using sticky sessions.
         */
        private void prepareKeys(SessionData session) {
            sessionKey = sessionKey(session.getId());
            sessionExpireInSeconds = session.getMaxInactiveInterval();
            expireCleanupInstant = roundUpToNextMinute(session.expiresAt());
            expirationsKey = getExpirationsKey(expireCleanupInstant);
            if (sessionConfigurationService.isSticky()) {
                forceCleanupInstant = roundUpToNextMinute(expireCleanupInstant);
                forceExpirationsKey = getForcedExpirationsKey(forceCleanupInstant);
            } else {
                forceCleanupInstant = 0;
                forceExpirationsKey = null;
            }
        }

        private void manageCleanupKeys(SessionData session) {
            if (!session.isNew()) {
                long originalCleanupInstant = roundUpToNextMinute(session.getOriginalLastAccessed());
                if (expireCleanupInstant != originalCleanupInstant) {
                    byte[] originalExpirationsKey = getExpirationsKey(originalCleanupInstant);
                    redis.srem(originalExpirationsKey, sessionKey);
                    if (sessionConfigurationService.isSticky()) {
                        long originalForceCleanupInstant = roundUpToNextMinute(expireCleanupInstant);
                        byte[] originalForcedExpirationsKey = getForcedExpirationsKey(originalForceCleanupInstant);
                        redis.srem(originalForcedExpirationsKey, sessionKey);
                    }
                } else if (sessionExpireInSeconds <= 0) {
                    // If session doesn't expire, remove it from expirations key
                    redis.srem(expirationsKey, sessionKey);
                    if (sessionConfigurationService.isSticky()) {
                        redis.srem(forceExpirationsKey, sessionKey);
                    }
                }
            }
        }
    }

    /**
     * Subscription task that listens to redis expiration notification. If an
     * exception while listening to notifications occurs, an exponential back-off
     * strategy is applied. The task will retry to establish connection in
     * increasing interval until it succeeds or until maximum number of retries
     * has been made.
     */
    class SubscriptionRunner implements Runnable {
        RedisConnector redis = dependencyService.getRedisConnector();
        int attempt;
        long lastConnect;
        String keyExpirePrefix = sessionConfigurationService.isSticky() ? constructKeyExpirePrefix(sessionConfigurationService.getNode()) : getKeyPrefix();

        @Override
        public void run() {
            LOGGER.info("Registering subscriber for expiration events.");
            lastConnect = System.currentTimeMillis();
            attempt = 0;
            while (true) {
                try {
                    // Currently listening to all databases __keyevent@*:expire
                    expirationListener = new ExpirationListener(sessionManager, keyExpirePrefix);
                    expirationListener.start(redis);
                    LOGGER.info("Stopped subscribing for expiration events.");
                    return;
                } catch (Exception e) { // NOSONAR
                    if (Thread.interrupted()) {
                        return;
                    }
                    if (redis.isRedisException(e) && e.getCause() instanceof InterruptedException) {
                        LOGGER.warn("Interrupted subscribtion for expiration events.");
                        return;
                    }
                    retryOnException(e);
                    if (Thread.interrupted()) {
                        return;
                    }
                }
            }
        }

        /**
         * When an exception occurs we will retry connect with a back-off
         * strategy.
         *
         * @param e
         *            exception that occurred
         */
        void retryOnException(Exception e) {
            LOGGER.error("Failure during subscribing to redis events. Will be retrying...", e);
            long instant = System.currentTimeMillis();
            long delta = instant - lastConnect;
            // If last connectivity was long time ago, forget it.
            if (delta > RedisConstants.RESET_RETRY_THRESHOLD) {
                attempt = 0;
            } else {
                attempt++;
                if (attempt >= RedisConstants.MAX_CONNECTION_ERRORS) {
                    LOGGER.error("Unable to connect to redis servers after trying {} times. "
                            + "Stopped listening to expiration events.", attempt);
                    throw new IllegalStateException("Stopped listening to expiration events.", e);
                } else {
                    doWait();
                    // Assume this connectivity will succeed.
                    lastConnect = instant;
                }
            }
        }

        /**
         * Wait using exponential back-off strategy.
         */
        void doWait() {
            try {
                Thread.sleep(getDelay()); // NOSONAR
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        long getDelay() {
            return SECONDS.toMillis(RedisConstants.FIBONACCI_DELAY_PATTERN[attempt]);
        }
    }
}