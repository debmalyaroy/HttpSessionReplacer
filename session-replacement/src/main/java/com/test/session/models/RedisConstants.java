package com.test.session.models;

import static java.util.concurrent.TimeUnit.SECONDS;
import static redis.clients.util.SafeEncoder.encode;

import java.util.concurrent.TimeUnit;

public interface RedisConstants {
    /**
     * Default redis timeout.
     */
    int DEFAULT_REDIS_TIMEOUT = 2000;

    /**
     * Default size of the redis pool.
     */
    int DEFAULT_REDIS_POOL_SIZE = 100;

    int DEFAULT_REDIS_PORT = 6379;
    String[] DEFAULT_REDIS_HOST = {"localhost:6379"};

    /**
     * The default prefix for each key and channel in Redis used by Session
     * management
     */
    String DEFAULT_SESSION_PREFIX = "com.test.session";

    /**
     * Default name for redis master when using sentinel mode.
     */
    String DEFAULT_REDIS_MASTER_NAME = DEFAULT_SESSION_PREFIX;

    String SORTED_SET_STRATEGY_NAME = "Sorted Set";
    String SORTED_SET_STRATEGY_VALUE = "ZRANGE";
    String NOTIFICATION_STRATEGY_NAME = "Notification";
    String NOTIFICATION_STRATEGY_VALUE = "NOTIF";
    String PUB_SUB_STRATEGY_NAME = "Publish Subscribe";
    String PUB_SUB_STRATEGY_VALUE = "PUBSUB";
    String USE_IPV4_NAME = "Use IPV4";
    String USE_IPV4_VALUE = "IPV4";
    String USE_IPV6_NAME = "Use IPV6";
    String USE_IPV6_VALUE = "IPV6";
    String REDIS_MODE_SINGLE = "SINGLE";
    String REDIS_MODE_SENTINEL = "SENTINEL";
    String REDIS_MODE_CLUSTER = "CLUSTER";
    String JEDIS_POOL_CONNECTOR_PID = "jedisPool";
    String JEDIS_CLUSTER_CONNECTOR_PID = "jedisCluster";

    String CRLF = "\r\n";
    String REDIS_VERSION_LABEL = "redis_version:";
    Integer[] MIN_MULTISPOP_VERSION = new Integer[] { 3, 2 };
    
    String ALLSESSIONS_KEY = "com.test.session:all-sessions-set:";
    int SESSION_PERSISTENCE_SAFETY_MARGIN = (int) TimeUnit.MINUTES.toSeconds(5);
    long SESSION_PERSISTENCE_SAFETY_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(5);
    
    long ONE_MINUTE = TimeUnit.MINUTES.toSeconds(1);

    int SPOP_BULK_SIZE = 1000;

    byte[] EMPTY_STRING = encode("");
    String DEFAULT_SESSION_EXPIRE_PREFIX = "com.test.session:expire";
    byte[] DEFAULT_SESSION_EXPIRE_PREFIX_BUF = encode(DEFAULT_SESSION_EXPIRE_PREFIX);
    /**
     * After this number of milliseconds, forget that there was an issue with
     * connectivity. 377 is 14th Fibonacci's number
     */
    long RESET_RETRY_THRESHOLD = SECONDS.toMillis(377);
    /**
     * Exponential back-off is based on Fibonacci numbers - i.e. wait 0 seconds,
     * 1 second, 1 second, 3 second etc.
     */
    int[] FIBONACCI_DELAY_PATTERN = new int[] { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144,
            233 };
    /**
     * Maximum number of retries before aborting exponential back-off.
     */
    int MAX_CONNECTION_ERRORS = FIBONACCI_DELAY_PATTERN.length;

    /**
     * 10 second cleanup interval for Sorted Set Expiry
     */
    int REGULAR_CLEANUP_INTERVAL = 10;
    
    // We subscribe to expired events
    String EXPIRY_SUBSCRIPTION_PATTERN = "__keyevent@*__:expired";
    
    // Suffix for expired notifications
    byte[] EXPIRED_SUFFIX = encode(":expired");

    /**
     * Meta attribute for timestamp (Unix time) of last access to session.
     */
    byte[] LAST_ACCESSED = encode("#:lastAccessed");
    /**
     * Meta attribute for maximum inactive interval of the session (in seconds).
     */
    byte[] MAX_INACTIVE_INTERVAL = encode("#:maxInactiveInterval");
    /**
     * Meta attribute for timestamp (Unix time) of the session creation.
     */
    byte[] CREATION_TIME = encode("#:creationTime");
    /**
     * Meta attribute that contains mark if the session is invalid (being
     * deleted or marked as invalid via API).
     */
    byte[] INVALID_SESSION = encode("#:invalidSession");
    /**
     * Meta attribute for the node owning the session.
     */
    byte[] OWNER_NODE = encode("#:owner");
    /**
     * Representation of true value
     */
    byte[] BYTES_TRUE = encode(String.valueOf(1));

    /**
     * All attributes starting with #: are internal (meta-atrributes).
     */
    byte[] REDIS_INTERNAL_PREFIX = new byte[] { '#', ':' };
}
