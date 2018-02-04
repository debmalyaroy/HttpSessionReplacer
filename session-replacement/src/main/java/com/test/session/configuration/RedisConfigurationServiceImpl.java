package com.test.session.configuration;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.PropertyUnbounded;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.RedisConfigurationService;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPoolConfig;

/**
 * This class encapsulates configuration of Redis servers. It provides helper
 * methods to read configuratin, resolve server/sentinel/cluster member names,
 * and configure JedisPool.
 */
@Component(immediate = true, metatype = true)
@Service(RedisConfigurationService.class)
public class RedisConfigurationServiceImpl implements RedisConfigurationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfigurationServiceImpl.class);

    @Property(label = "Enable redis repository", description = "Configuration property that if checked will enable Redis repository.", boolValue = false)
    private static final String ENABLE_REDIS = "redis.enable";

    @Property(label = "IP version", description = "Configuration property that specifies IP version to be used with Redis.", options = {
            @PropertyOption(name = "true", value = USE_IPV4_VALUE),
            @PropertyOption(name = "false", value = USE_IPV6_VALUE) }, value = "false")
    private static final String REDIS_USE_IPV = "redis.ipv";

    @Property(label = "Redis clustering mode", description = "Configuration property that specifies the redis clustering mode. Can be SINGLE, SENTINEL or CLUSTER.", options = {
            @PropertyOption(name = REDIS_MODE_SINGLE, value = REDIS_MODE_SINGLE),
            @PropertyOption(name = REDIS_MODE_SENTINEL, value = REDIS_MODE_SENTINEL),
            @PropertyOption(name = REDIS_MODE_CLUSTER, value = REDIS_MODE_CLUSTER) }, value = REDIS_MODE_SINGLE)
    private static final String REDIS_CLUSTER_MODE = "redis.mode";

    @Property(label = "Redis master name (sentinel mode)", description = "Configuration property that specifies the name of redis master when using sentinel mode.", value = StringUtils.EMPTY)
    private static final String REDIS_MASTER_NAME = "redis.master";

    @Property(label = "Redis server host", description = "Configuration property that specifies the address(es) and optionally port(s) of redis servers or sentinels.\n"
            + "\t\t* For a single instance this is can have host:port.\n"
            + "\t\t* For a single instance if this doesn't have port, then the port value should be provided below.\n"
            + "\t\t* For a sentinal or cluster instance this is should have host:port.", unbounded = PropertyUnbounded.ARRAY, cardinality = Integer.MAX_VALUE)
    private static final String REDIS_HOST = "redis.host";

    @Property(label = "Redis server port", description = "Configuration property that specifies port of redis server(s) or sentinel(s).", intValue = DEFAULT_REDIS_PORT)
    private static final String REDIS_PORT = "redis.port";

    @Property(label = "Timeout (in sec)", description = "Configuration property that specifies connection and socket timeout used by redis.", intValue = DEFAULT_REDIS_TIMEOUT)
    private static final String REDIS_TIMEOUT = "redis.timeout";

    @Property(label = "Size of Redis pool", description = "Configuration property that specifies the size of the pool of redis connections.", intValue = DEFAULT_REDIS_POOL_SIZE)
    private static final String REDIS_POOL_SIZE = "redis.pool";

    @Property(label = "Expiration Strategy", description = "Configuration property that specifies expiration strategy used by redis.", options = {
            @PropertyOption(name = SORTED_SET_STRATEGY_VALUE, value = SORTED_SET_STRATEGY_VALUE),
            @PropertyOption(name = NOTIFICATION_STRATEGY_VALUE, value = NOTIFICATION_STRATEGY_VALUE) }, value = NOTIFICATION_STRATEGY_VALUE)
    private static final String REDIS_EXPIRATION_STRATEGY = "redis.expiration";

    private boolean enableRedis;
    private boolean supportIpV6;
    private boolean supportIpV4 = !supportIpV6;
    private String clusterMode;
    private String masterName;
    private String[] servers;
    private int port;
    private int timeout;
    private int poolSize;
    private String strategy;

    @Activate
    protected void onActivate(Map<String, ?> properties) {
        enableRedis = PropertiesUtil.toBoolean(properties.get(ENABLE_REDIS), false);
        supportIpV6 = Boolean.parseBoolean(PropertiesUtil.toString(properties.get(REDIS_USE_IPV), "false"));
        clusterMode = PropertiesUtil.toString(properties.get(REDIS_CLUSTER_MODE), REDIS_MODE_SINGLE);
        masterName = PropertiesUtil.toString(properties.get(REDIS_MASTER_NAME), StringUtils.EMPTY);
        servers = PropertiesUtil.toStringArray(properties.get(REDIS_HOST), DEFAULT_REDIS_HOST);
        port = PropertiesUtil.toInteger(properties.get(REDIS_PORT), DEFAULT_REDIS_PORT);
        timeout = PropertiesUtil.toInteger(properties.get(REDIS_TIMEOUT), DEFAULT_REDIS_TIMEOUT);
        poolSize = PropertiesUtil.toInteger(properties.get(REDIS_POOL_SIZE), DEFAULT_REDIS_POOL_SIZE);
        strategy = PropertiesUtil.toString(properties.get(REDIS_EXPIRATION_STRATEGY), NOTIFICATION_STRATEGY_VALUE);

        LOGGER.debug("Redis configuration details: {}", toString());
    }

    @Modified
    protected void onModification(Map<String, ?> properties) {
        onActivate(properties);
    }

    @Deactivate
    protected void onDeactivation(Map<String, ?> properties) {

    }

    @Override
    public boolean isRedisEnabled() {
        return enableRedis;
    }

    @Override
    public boolean isSupportIpV6() {
        return supportIpV6;
    }

    @Override
    public boolean isSupportIpV4() {
        return supportIpV4;
    }

    @Override
    public String getClusterMode() {
        return clusterMode;
    }

    @Override
    public String getStrategy() {
        return strategy;
    }

    @Override
    public int getTimeOut() {
        return timeout;
    }

    @Override
    public String getMasterName() {
        return masterName;
    }

    /**
     * Returns set of sentinel servers
     *
     * @return
     */
    public Set<String> sentinels() {
        return new LinkedHashSet<>(Arrays.asList(servers));
    }

    /**
     * Configures Jedis pool of connection.
     * 
     * @param config
     *
     * @return configured Jedis pool of connection.
     */
    public JedisPoolConfig configuredPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();

        poolConfig.setMaxTotal(poolSize);
        poolConfig.setMaxIdle(Math.min(poolConfig.getMaxIdle(), poolConfig.getMaxTotal()));
        poolConfig.setMinIdle(Math.min(poolConfig.getMinIdle(), poolConfig.getMaxIdle()));

        return poolConfig;
    }

    /**
     * Extracts jedis host/port configuration
     * 
     * @param config
     */
    public Set<HostAndPort> jedisHostsAndPorts() {
        Set<HostAndPort> hostAndPort = new LinkedHashSet<>();

        try {
            for (String aServer : servers) {
                String[] serverAndPort = aServer.split(":");

                int portToUse = portToUse(serverAndPort);
                InetAddress[] hosts = resolveServers(serverAndPort[0]);

                for (InetAddress host : hosts) {
                    if (isIpSupported(host)) {
                        hostAndPort.add(new HostAndPort(host.getHostAddress(), portToUse));
                    }
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve cluster host for configuration", e);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Port paramter was in server configuration. Expecting numeric values, but it was not integer");
        }

        LOGGER.debug("Resolved hosts from '{}':{} are {}", servers, port, hostAndPort);
        return hostAndPort;
    }

    /**
     * Returns port to use either from server:port pair, or default port.
     *
     * @param serverAndPort
     *            server and optional port pair
     * @param defaultPort
     *            default port o use
     * @return port to use
     */
    private int portToUse(String[] serverAndPort) {
        if (serverAndPort.length > 1) {
            return Integer.parseInt(serverAndPort[1]);
        }

        return port;
    }

    /**
     * Resolves server DNS name if needed. Retrieves all IP addresses associated
     * with DNS name.
     *
     * @param serverName
     *            DNS name or IP address
     * @return list of IP addresses associated with DNS name
     * @throws UnknownHostException
     *             if server name is not recognized by DNS
     */
    private InetAddress[] resolveServers(String serverName) throws UnknownHostException {
        InetAddress[] hosts = InetAddress.getAllByName(serverName);

        LOGGER.debug("Resolved hosts from '{}', parsed={} resolved={}", servers, serverName, Arrays.asList(hosts));
        return hosts;
    }

    /**
     * Check if IP address is allowed: e.g. is address IPv6 or IPv4 and is that
     * type of IP addresses allowed).
     *
     * @param host
     *            IP address of the host
     * @return if IP address is supported
     */
    private boolean isIpSupported(InetAddress host) {
        if (host instanceof Inet6Address) {
            return supportIpV6;
        }

        return supportIpV4;
    }

    @Override
    public String getNameSpace() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String toString() {
        return String.format(
                "RedisConfigurationServiceImpl [supportIpV6=%s, supportIpV4=%s, clusterMode=%s, masterName=%s, servers=%s, port=%s, timeout=%s, poolSize=%s, strategy=%s]",
                supportIpV6, supportIpV4, clusterMode, masterName, Arrays.toString(servers), port, timeout, poolSize,
                strategy);
    }
}