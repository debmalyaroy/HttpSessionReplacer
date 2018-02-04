package com.test.session.connection;

import static redis.clients.util.SafeEncoder.encode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.RedisConfigurationService;
import com.test.session.connection.api.RedisConnector;
import com.test.session.models.RedisConstants;

import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Transaction;
import redis.clients.util.Pool;

/**
 * This class hides difference of APIs between {@link Jedis} and
 * {@link JedisCluster}. The implementation offers subset of
 * {@link BinaryJedisCommands}.
 */
@Component(immediate = true, name = RedisConstants.JEDIS_POOL_CONNECTOR_PID)
@Service(RedisConnector.class)
public class JedisPoolConnector extends AbstractJedisConnector {
    private static final Logger LOGGER = LoggerFactory.getLogger(JedisPoolConnector.class);

    private Pool<Jedis> jedisPool;
    private final ThreadLocal<Jedis> currentJedis = new ThreadLocal<>();

    @Reference(bind = "bindRedisConfigurationService")
    private RedisConfigurationService redisConfigurationService;

    protected void bindRedisConfigurationService(final RedisConfigurationService service, Map<String, ?> properties) {
        this.redisConfigurationService = service;

        try {
            switch (redisConfigurationService.getClusterMode()) {
                case RedisConstants.REDIS_MODE_SINGLE:
                    singleInstance();
                case RedisConstants.REDIS_MODE_SENTINEL:
                    sentinelInstance();
                default:
                    // Don't create the pool
            }
        } catch (Exception ex) {
            LOGGER.error("Error while setting up connector.", ex);
        }
    }

    @Override
    public void requestFinished() {
        Jedis jedis = currentJedis.get();

        if (jedis != null) {
            currentJedis.set(null);
            jedis.close();
        }
    }

    @Override
    public void psubscribe(final RedisPubSub listener, String pattern) {
        BinaryJedisPubSub bps = getBinaryJedisPubSub(listener);

        listener.link(bps);
        jedis().psubscribe(bps, encode(pattern));
    }

    @Override
    public Long hdel(byte[] key, byte[]... fields) {
        return jedis().hdel(key, fields);
    }

    @Override
    public List<byte[]> hmget(byte[] key, byte[]... fields) {
        return jedis().hmget(key, fields);
    }

    @Override
    public String hmset(byte[] key, Map<byte[], byte[]> hash) {
        return jedis().hmset(key, hash);
    }

    @Override
    public Long hsetnx(final byte[] key, final byte[] field, final byte[] value) {
        return jedis().hsetnx(key, field, value);
    }

    @Override
    public Long hset(final byte[] key, final byte[] field, final byte[] value) {
        return jedis().hset(key, field, value);
    }

    @Override
    public Set<byte[]> hkeys(byte[] key) {
        return jedis().hkeys(key);
    }

    @Override
    public String set(byte[] key, byte[] value) {
        return jedis().set(key, value);
    }

    @Override
    public String setex(byte[] key, int expiry, byte[] value) {
        return jedis().setex(key, expiry, value);
    }

    @Override
    public Long expire(byte[] key, int value) {
        return jedis().expire(key, value);
    }

    @Override
    public void srem(byte[] key, byte[]... member) {
        jedis().srem(key, member);
    }

    @Override
    public Long sadd(byte[] key, byte[]... member) {
        return jedis().sadd(key, member);
    }

    @Override
    public Long del(byte[]... keys) {
        return jedis().del(keys);
    }

    @Override
    public Boolean exists(byte[] key) {
        return jedis().exists(key);
    }

    @Override
    public Set<byte[]> smembers(byte[] key) {
        return jedis().smembers(key);
    }

    @Override
    public Set<byte[]> spop(byte[] key, long count) {
        return jedis().spop(key, count);
    }

    @Override
    public Long expireAt(byte[] key, long unixTime) {
        return jedis().expireAt(key, unixTime);
    }

    @Override
    public Long zadd(byte[] key, double score, byte[] elem) {
        return jedis().zadd(key, score, elem);
    }

    @Override
    public Long zrem(byte[] key, byte[]... fields) {
        return jedis().zrem(key, fields);
    }

    @Override
    public Set<byte[]> zrangeByScore(byte[] key, double start, double end) {
        return jedis().zrangeByScore(key, start, end);
    }

    @Override
    public Set<byte[]> zrange(byte[] key, long start, long end) {
        return jedis().zrange(key, start, end);
    }

    @Override
    public Long persist(byte[] key) {
        return jedis().persist(key);
    }

    @Override
    public String info(String section) {
        return jedis().info(section);
    }

    @Override
    public <T> ResponseFacade<T> transaction(final byte[] key, final TransactionRunner<T> transaction) {
        final Transaction t = jedis().multi();
        ResponseFacade<T> response = transaction.run(wrapJedisTransaction(t));
        t.exec();

        return response;
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Override
    public String rename(byte[] oldkey, byte[] newkey) {
        return jedis().rename(oldkey, newkey);
    }

    @Override
    public byte[] get(byte[] key) {
        return jedis().get(key);
    }

    @Override
    public Long publish(byte[] channel, byte[] message) {
        return jedis().publish(channel, message);
    }

    private void singleInstance() {
        // Even if multiple servers defined, select the first one
        Set<HostAndPort> hostAndPorts = redisConfigurationService.jedisHostsAndPorts();

        if (CollectionUtils.isNotEmpty(hostAndPorts)) {
            HostAndPort redisServer = hostAndPorts.iterator().next();

            jedisPool = new JedisPool(redisConfigurationService.configuredPool(), redisServer.getHost(), redisServer.getPort(), redisConfigurationService.getTimeOut());
        }
    }

    private void sentinelInstance() {
        jedisPool = new JedisSentinelPool(redisConfigurationService.getMasterName(),
                redisConfigurationService.sentinels(), redisConfigurationService.configuredPool(), redisConfigurationService.getTimeOut());
    }

    private Jedis jedis() {
        Jedis jedis = currentJedis.get();

        if (jedis == null && jedisPool != null) {
            jedis = jedisPool.getResource();
            currentJedis.set(jedis);
        }

        return jedis;
    }
}
