package com.test.session.connection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.test.session.connection.api.RedisConnector;

import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Transaction;

/**
 * Base class for jedis facades. Contains methods that are common for both
 * single/sentinel node facade and cluster facade.
 *
 */
public abstract class AbstractJedisConnector implements RedisConnector {
    private static final String CRLF = "\r\n";
    private static final String REDIS_VERSION_LABEL = "redis_version:";
    private static final Integer[] MIN_MULTISPOP_VERSION = new Integer[] { 3, 2 };

    private List<Integer> version;

    @Override
    public void punsubscribe(final RedisPubSub listener, byte[] pattern) {
        listener.getLinked().punsubscribe(pattern);
    }

    @Override
    public void requestFinished() {
        // Do nothing
    }

    @Override
    public boolean supportsMultiSpop() {
        readVersion();

        for (int i = 0; i < MIN_MULTISPOP_VERSION.length && i < version.size(); i++) {
            if (version.get(i) < MIN_MULTISPOP_VERSION[i]) {
                return false;
            }
        }

        return true;
    }

    protected BinaryJedisPubSub getBinaryJedisPubSub(final RedisPubSub listener) {
        BinaryJedisPubSub bps = new BinaryJedisPubSub() {
            @Override
            public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {
                listener.onPMessage(pattern, channel, message);
            }
        };

        return bps;
    }

    private void readVersion() {
        if (version == null) {
            String info = info("server");

            if (info != null) {
                version = parseVersion(info);
            }

            if (version == null) {
                version = Collections.singletonList(0);
            }
        }
    }

    private static List<Integer> parseVersion(String info) {
        int start = info.indexOf(REDIS_VERSION_LABEL);

        if (start >= 0) {
            start += REDIS_VERSION_LABEL.length();
            // In RESP different parts of the protocol are always terminated
            // with "\r\n" (CRLF).
            int end = info.indexOf(CRLF, start);

            if (end < 0) {
                end = info.length();
            }

            String[] coordiantes = info.substring(start, end).split("\\.");

            return Arrays.stream(coordiantes)
                    .map(coordinate -> Integer.parseInt(coordinate))
                    .collect(Collectors.toList());
        }

        return null;
    }

    protected static TransactionFacade wrapJedisTransaction(final Transaction t) {
        return new TransactionFacade() {
            @Override
            public void hdel(byte[] key, byte[]... fields) {
                t.hdel(key, fields);
            }

            @Override
            public void hmset(byte[] key, Map<byte[], byte[]> hash) {
                t.hmset(key, hash);
            }

            @Override
            public void del(byte[]... keys) {
                t.del(keys);
            }

            @Override
            public ResponseFacade<Set<byte[]>> smembers(final byte[] key) {
                return () -> t.smembers(key).get();
            }
        };
    }
}