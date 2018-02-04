package com.test.session.api;

import java.util.Set;

import com.test.session.models.RedisConstants;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPoolConfig;

/**
 * This class encapsulates configuration of Redis servers. It provides helper
 * methods to read configuratin, resolve server/sentinel/cluster member names,
 * and configure JedisPool.
 */
public interface RedisConfigurationService extends RedisConstants {
    boolean isRedisEnabled();

    boolean isSupportIpV6();

    boolean isSupportIpV4();

    String getClusterMode();

    String getStrategy();

    Set<String> sentinels();

    JedisPoolConfig configuredPool();

    Set<HostAndPort> jedisHostsAndPorts();

    int getTimeOut();

    String getMasterName();

    String getNameSpace();
}