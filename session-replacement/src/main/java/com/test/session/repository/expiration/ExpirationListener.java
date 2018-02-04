package com.test.session.repository.expiration;

import static redis.clients.util.SafeEncoder.encode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionManager;
import com.test.session.connection.api.RedisConnector;
import com.test.session.connection.api.RedisConnector.RedisPubSub;
import com.test.session.models.RedisConstants;
import com.test.session.repository.RedisSessionRepository;

import redis.clients.jedis.BinaryJedisPubSub;

/**
 * This class listens to expiration events coming from Redis, and when the event
 * specifies a key starting with
 * {@link RedisSessionRepository#DEFAULT_SESSION_EXPIRE_PREFIX}, it tries to
 * expire corresponding session
 */
public class ExpirationListener implements RedisPubSub {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExpirationListener.class);

    private final SessionManager sessionManager;
    private final byte[] keyPrefix;
    private boolean subsrcibed;
    private BinaryJedisPubSub linkedImplementation;

    public ExpirationListener(SessionManager sessionManager, String keyPrefix) {
        this.sessionManager = sessionManager;
        this.keyPrefix = encode(keyPrefix);
    }

    /**
     * Starts subscription to redis notifications. This is a blocking operation
     * and the thread which called this method will block on opened socket.
     *
     * @param redis
     *            facade to redis library
     */
    @Override
    public void start(RedisConnector redis) {
        if (!subsrcibed) {
            subsrcibed = true;
            redis.psubscribe(this, RedisConstants.EXPIRY_SUBSCRIPTION_PATTERN);
        }
    }

    @Override
    public void onPMessage(byte[] pattern, byte[] channelBuf, byte[] message) {
        // Only accept messages expiration notification channel
        // and only those that match our key prefix.
        if (channelBuf == null || message == null) {
            return;
        }

        if (!isExpiredChannel(channelBuf)) {
            return;
        }

        if (!isExpireKey(message)) {
            return;
        }

        String body = encode(message);
        LOGGER.debug("Got notification for channel: '{}', body: '{}'", encode(channelBuf), body);

        String sessionId = extractSessionId(body);
        LOGGER.info("Session expired event for sessionId: '{}'", sessionId);

        // We run session delete in another thread, otherwise we would block listener.
        // TODO: Find the session data and send
        // sessionManager.deleteAsync(sessionId, true);
    }

    /**
     * Stops subscription to redis notifications. Call to this method will
     * unblock thread waiting on PSUBSCRIBE.
     *
     * @param redis
     *            facade to redis library
     */
    @Override
    public void close(RedisConnector redis) {
        if (subsrcibed) {
            redis.punsubscribe(this, encode(RedisConstants.EXPIRY_SUBSCRIPTION_PATTERN));
            subsrcibed = false;
        }
    }

    @Override
    public BinaryJedisPubSub getLinked() {
        return linkedImplementation;
    }

    @Override
    public void link(BinaryJedisPubSub linkedImplementation) {
        this.linkedImplementation = linkedImplementation;
    }

    /**
     * Checks if channel identifies expired notification channel.
     *
     * @param channelBuf
     *            array containing channel information
     * @return <code>true</code> if channel identifies expired notifications
     *         channel
     */
    private boolean isExpiredChannel(byte[] channelBuf) {
        int suffixLength = RedisConstants.EXPIRED_SUFFIX.length;
        int channelPos = channelBuf.length - suffixLength;

        if (channelPos <= 0) {
            return false;
        }

        for (int i = 0; i < suffixLength; i++, channelPos++) {
            if (channelBuf[channelPos] != RedisConstants.EXPIRED_SUFFIX[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean isExpireKey(byte[] message) {
        int prefixLength = keyPrefix.length;

        if (message.length < prefixLength) {
            return false;
        }

        for (int i = 0; i < prefixLength; i++) {
            if (message[i] != keyPrefix[i]) {
                return false;
            }
        }

        return true;
    }

    private static String extractSessionId(String body) {
        int beginIndex = body.lastIndexOf(':') + 1;
        String sessionId = body.substring(beginIndex);
        int braceOpening = sessionId.indexOf('{');

        if (braceOpening >= 0) {
            int braceClosing = sessionId.indexOf('}', braceOpening + 1);

            if (braceClosing > braceOpening) {
                int idLen = sessionId.length();
                StringBuffer sb = new StringBuffer();

                if (braceOpening > 0) {
                    sb.append(sessionId, 0, braceOpening);
                }

                sb.append(sessionId, braceOpening + 1, braceClosing).append(sessionId, braceClosing + 1, idLen);
                sessionId = sb.toString();
            }
        }

        return sessionId;
    }
}
