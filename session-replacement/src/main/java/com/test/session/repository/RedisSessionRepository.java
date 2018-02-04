package com.test.session.repository;

import static org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_MULTIPLE;
import static org.apache.felix.scr.annotations.ReferencePolicy.DYNAMIC;
import static redis.clients.util.SafeEncoder.encode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.OSGiDependencyService;
import com.test.session.api.RedisConfigurationService;
import com.test.session.api.RedisExpirationStrategy;
import com.test.session.api.SerializerDeserializerService;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionRepository;
import com.test.session.connection.api.RedisConnector;
import com.test.session.connection.api.RedisConnector.TransactionRunner;
import com.test.session.models.RedisConstants;
import com.test.session.models.SessionConstants;
import com.test.session.models.SessionData;

/**
 * Main class for implementing Redis repository logic.
 */
@Component(immediate = true, name = SessionConstants.REDIS_REPOSITORY_VALUE)
@Service
@References({
    @Reference(referenceInterface = RedisExpirationStrategy.class, policy = DYNAMIC, cardinality = MANDATORY_MULTIPLE)
})
public class RedisSessionRepository implements SessionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSessionRepository.class);

    private static final int CREATION_TIME_INDEX = 2;
    private static final int INVALID_SESSION_INDEX = 3;
    private static final int OWNER_NODE_INDEX = 4;

    @Reference
    private SerializerDeserializerService serializer;

    @Reference
    private SessionConfigurationService sessionConfiguration;

    @Reference
    private OSGiDependencyService dependencyService;

    @Reference
    private RedisConfigurationService redisConfigurationService;

    private RedisConnector redis;

    private Map<String, RedisExpirationStrategy> redisExpirationStrategies = new HashMap<>();

    protected final void bindRedisExpirationStrategy(final RedisExpirationStrategy service, final Map<Object, Object> props) {
        redisExpirationStrategies.put(OSGiDependencyService.getProperty(props), service);
    }

    protected final void unbindRedisExpirationStrategy(final RedisExpirationStrategy service, final Map<Object, Object> props) {
        redisExpirationStrategies.remove(OSGiDependencyService.getProperty(props));
    }

    protected void bindOSGiDependencyService(final OSGiDependencyService service, Map<String, ?> properties) {
        this.dependencyService = service;
        redis = dependencyService.getRedisConnector();
    }

    @Override
    public CommitTransaction startCommit(SessionData session) {
        return new RedisSessionTransaction(session);
    }

    @Override
    public void remove(SessionData session) {
        redis.del(sessionKey(session.getId()));
        getExpiryManager().sessionDeleted(session);
    }

    @Override
    public Object getSessionAttribute(SessionData session, String attribute) {
        List<byte[]> values = redis.hmget(sessionKey(session), encode(attribute));
        return serializer.deserialize(values.get(0));
    }

    @Override
    public boolean prepareRemove(SessionData session) {
        Long result = redis.hsetnx(sessionKey(session.getId()), RedisConstants.INVALID_SESSION, RedisConstants.BYTES_TRUE);
        return result.intValue() == 1;
    }

    @Override
    public Set<String> getAllKeys(SessionData session) {
        return Collections.unmodifiableSet(redis.hkeys(sessionKey(session)).stream()
                                            .filter(key -> !hasInternalPrefix(key))
                                            .map(key -> encode(key))
                                            .collect(Collectors.toSet()));
    }

    @Override
    public void storeSessionData(SessionData sessionData) {
        Map<byte[], byte[]> attributes = new HashMap<>();

        addInt(attributes, RedisConstants.MAX_INACTIVE_INTERVAL, sessionData.getMaxInactiveInterval());
        addLong(attributes, RedisConstants.LAST_ACCESSED, sessionData.getLastAccessedTime());
        addLong(attributes, RedisConstants.CREATION_TIME, sessionData.getCreationTime());

        if (sessionConfiguration.isSticky()) {
            attributes.put(RedisConstants.OWNER_NODE, encode(sessionConfiguration.getNode()));
        }

        redis.hmset(sessionKey(sessionData.getId()), attributes);
        getExpiryManager().sessionTouched(sessionData);
    }

    @Override
    public void requestFinished() {
        redis.requestFinished();
    }

    @Override
    public void setSessionAttribute(SessionData session, String name, Object value) {
        redis.hset(sessionKey(session), encode(name), serializer.serialize(value));
    }

    @Override
    public void removeSessionAttribute(SessionData session, String name) {
        redis.hdel(sessionKey(session), encode(name));
    }

    @Override
    public Collection<String> getOwnedSessionIds() {
        // Redis repository doesn't support retrieval of session ids owned by node.
        return null;
    }

    @Override
    public void sessionIdChange(SessionData sessionData) {
        String newId = sessionData.getId();
        String oldId = sessionData.getOldSessionId();
        
        redis.rename(sessionKey(oldId), sessionKey(newId));
        redis.publish(getRedirectionChannel(), encode(oldId + ':' + newId));

        getExpiryManager().sessionIdChange(sessionData);
    }

    @Override
    public SessionData getSessionData(String id) {
        byte[] key = sessionKey(id);

        // If sticky session, retrieve last owner also
        List<byte[]> values = sessionConfiguration.isSticky()
                ? redis.hmget(key, RedisConstants.LAST_ACCESSED, RedisConstants.MAX_INACTIVE_INTERVAL, RedisConstants.CREATION_TIME, RedisConstants.INVALID_SESSION, RedisConstants.OWNER_NODE)
                : redis.hmget(key, RedisConstants.LAST_ACCESSED, RedisConstants.MAX_INACTIVE_INTERVAL, RedisConstants.CREATION_TIME, RedisConstants.INVALID_SESSION);

        if (!checkConsistent(id, values)) {
            return null;
        }

        long lastAccessed = longFrom(values.get(0));
        long creationTime = longFrom(values.get(CREATION_TIME_INDEX));
        String previousOwner = null;

        if (sessionConfiguration.isSticky()) {
            // For sticky sessions, we need to parse owner node and check if it is this one.
            byte[] prevOwnerBuffer = values.get(OWNER_NODE_INDEX);

            if (prevOwnerBuffer != null) {
                previousOwner = encode(prevOwnerBuffer);
                LOGGER.info("Retrieved session {}, last node {} to this node {}", id, previousOwner, sessionConfiguration.getNode());
            }
        }

        return new SessionData(id, lastAccessed, intFrom(values.get(1)), creationTime, previousOwner);
    }

    private RedisExpirationStrategy getExpiryManager() {
        return Optional.ofNullable(redisExpirationStrategies.get(redisConfigurationService.getStrategy()))
                .orElseThrow(() -> new IllegalArgumentException("No Redis Expiration Policy with name " + redisConfigurationService.getStrategy() + " is configured."));
    }

    private boolean checkConsistent(String sessionId, List<byte[]> values) {
        byte[] invalidSessionFlag = values.get(INVALID_SESSION_INDEX);

        // If we have invalid session flag, then session is (clearly) not valid
        if (invalidSessionFlag != null && invalidSessionFlag.length == 1 && invalidSessionFlag[0] == 1) {
            return false;
        }

        if (values.get(0) == null || values.get(1) == null) {
            if (values.get(0) != null || values.get(1) != null) {
                LOGGER.warn("Session in redis repository is not consistent for sessionId: '{}' "
                        + "One of last accessed (index 0 in array), max inactive interval (index 1 in array) was null: {}",
                        sessionId, values);
            }

            return false;
        }

        return true;
    }

    private byte[] sessionKey(SessionData session) {
        return sessionKey(session.getId());
    }

    private static boolean hasInternalPrefix(byte[] buf) {
        if (buf != null && buf.length > RedisConstants.REDIS_INTERNAL_PREFIX.length) {
            for (int i = 0; i < RedisConstants.REDIS_INTERNAL_PREFIX.length; i++) {
                if (RedisConstants.REDIS_INTERNAL_PREFIX[i] != buf[i]) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    private static int intFrom(byte[] b) {
        return ByteBuffer.wrap(b).getInt();
    }

    private static long longFrom(byte[] b) {
        return ByteBuffer.wrap(b).getLong();
    }

    private static void addLong(Map<byte[], byte[]> attributes, byte[] attr, long value) {
        ByteBuffer b = ByteBuffer.allocate(Long.BYTES);
        b.putLong(value);

        attributes.put(attr, b.array());
    }

    private static void addInt(Map<byte[], byte[]> attributes, byte[] attr, int value) {
        ByteBuffer b = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE);
        b.putInt(value);

        attributes.put(attr, b.array());
    }

    private byte[] sessionKey(String sessionId) {
        return encode(new StringBuffer()
                        .append(getKeyPrefix())
                        .append(sessionId)
                        .append('}')
                        .toString());
    }

    private String getKeyPrefix() {
        return RedisConstants.DEFAULT_SESSION_PREFIX + ":" + sessionConfiguration.getNamespace() + ":" + "{";
    }

    private byte[] getRedirectionChannel() {
        return encode(RedisConstants.DEFAULT_SESSION_PREFIX + ":" + sessionConfiguration.getNamespace() + ":" + "redirection");
    }

    /**
     * This class implements transaction that is executed at session commit time.
     * The transaction will store added/modified session attribute keys using single
     * HMSET redis command and it will also delete removed session attribute keys
     * using HDEL command. It uses underlying {@link RedisConnector} support for
     * transactions on the session key (redis MULTI command), and executes those
     * those commands in atomic way. The meta-attribute for transactions are also
     * updated.
     */
    private final class RedisSessionTransaction implements CommitTransaction {
        private final byte[] key;
        private final SessionData session;
        private final Map<byte[], byte[]> attributes = new HashMap<>();
        private final List<byte[]> toRemove = new ArrayList<>();

        private RedisSessionTransaction(SessionData session) {
            key = sessionKey(session.getId());
            this.session = session;
        }

        @Override
        public void changeAttribute(String attribute, Object value) {
            if (value == null) {
                toRemove.add(encode(attribute));
            } else {
                attributes.put(encode(attribute), serializer.serialize(value));
            }
        }

        /**
         * During commit, we add meta/attributes. See
         * {@link RedisSessionRepository#getSessionData(String)}. for list of meta
         * attributes.
         */
        @Override
        public void commit() {
            if (session.isNew()) {
                addLong(attributes, RedisConstants.CREATION_TIME, session.getCreationTime());
            }

            addInt(attributes, RedisConstants.MAX_INACTIVE_INTERVAL, session.getMaxInactiveInterval());
            addLong(attributes, RedisConstants.LAST_ACCESSED, session.getLastAccessedTime());

            if (sessionConfiguration.isSticky()) {
                attributes.put(RedisConstants.OWNER_NODE, encode(sessionConfiguration.getNode()));
            }

            redis.transaction(key, getTransactionRunner());
            getExpiryManager().sessionTouched(session);
        }

        private TransactionRunner<String> getTransactionRunner() {
            return (transaction) -> {
                if (!toRemove.isEmpty()) {
                    byte[][] arr = toRemove.toArray(new byte[0][]);
                    transaction.hdel(key, arr);
                }

                if (!attributes.isEmpty()) {
                    transaction.hmset(key, attributes);
                }

                return () -> "OK";
            };
        }
    }
}
