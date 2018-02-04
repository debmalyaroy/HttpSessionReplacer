package com.test.session.configuration;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.RedisConfigurationService;
import com.test.session.api.SessionConfigurationService;

@Component(immediate = true, metatype = true, name = "Session Management Configuration")
@Service(SessionConfigurationService.class)
public class SessionConfiguration implements SessionConfigurationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionConfiguration.class);

    @Property(label = "Disable session management", description = "Disable redis session management.", boolValue = true)
    private static final String DISABLED_SESSION = "session.disabled";

    @Property(label = "Session timeout", description = "Session timeout in seconds.", intValue = DEFAULT_SESSION_TIMEOUT)
    private static final String SESSION_TIMEOUT = "session.timeout";

    @Property(label = "Enable sticky session", description = "Activates sticky session strategy. "
            + "When activated, implementation should try to handle all session activity on the last node "
            + "that processed client request that impacts the session.", boolValue = false)
    private static final String STICKY_SESSIONS = "session.sticky";

    @Property(label = "Session namespace", description = "The namespace for sessions. It is best practice to have sessions in "
            + "different applications or webapps should having different namespaces. If applications want to share sessions, "
            + "then they can use same namespace. In case of webapps, namespace can be defined using servlet init parameter, "
            + "or if not present, the context name of the webapp is used as namespace.", value = DEFAULT_SESSION_NAMESPACE)
    private static final String SESSION_NAMESPACE = "session.namespace";

    @Property(label = "Generate session timestamp", description = "Indicate if generated session suffixed with Timestamp.", boolValue = false)
    private static final String SESSION_TIMESTAMP = "session.timestamp";

    @Property(label = "Hostname", description = "Enables overriding the name of the host. By default it is retrieved from environment.", value = StringUtils.EMPTY)
    private static final String SESSION_HOST = "session.host";

    @Property(label = "Session ID name", description = "The name of the cookie or URL element for propagating session.", value = DEFAULT_SESSION_ID_NAME)
    private static final String SESSION_ID_NAME = "session.sessionName";

    @Property(label = "Sessoin ID length", description = "Session id length when using RandomIdProvider.", intValue = DEFAULT_SESSION_ID_LENGTH)
    private static final String SESSION_ID_LENGTH = "session.id.length";

    @Property(label = "Delegate print writer", description = "Set to true if session should delegate PrintWriter implementation to container. "
            + "By default it is false and session replacer provides it's own implementation.", boolValue = false)
    private static final String DELEGATE_WRITER = "session.delegate.writer";

    @Property(label = "Repository factory name", description = "The name of SessionRepository implementation class.", options = {
            @PropertyOption(name = IN_MEMORY_REPOSITORY_VALUE, value = IN_MEMORY_REPOSITORY_VALUE),
            @PropertyOption(name = REDIS_REPOSITORY_VALUE, value = REDIS_REPOSITORY_VALUE)}, value = REDIS_REPOSITORY_VALUE)
    private static final String REPOSITORY_FACTORY_NAME = "session.repository.factory";

    @Property(label = "Session propagation method", description = "How session propagation will happen - using cookies or using URL.", options = {
            @PropertyOption(name = COOKIE_SESSION_PROPAGATION_TYPE_VALUE, value = COOKIE_SESSION_PROPAGATION_TYPE_VALUE),
            @PropertyOption(name = URL_SESSION_PROPAGATION_TYPE_VALUE, value = URL_SESSION_PROPAGATION_TYPE_VALUE)}, value = COOKIE_SESSION_PROPAGATION_TYPE_VALUE)
    private static final String SESSION_PROPAGATOR_NAME = "session.tracking";

    @Property(label = "Session replication trigger", description = "Strategy for for triggering replication.", options = {
            @PropertyOption(name = SET_AND_NON_PRIMITIVE_GET, value = "true"),
            @PropertyOption(name = SET, value = "false")}, value = "true")
    private static final String SESSION_REPLICATION_TRIGGER = "session.replication-trigger";

    @Property(label = "Session ID provider", description = "Strategy for for generating session ID.", options = {
            @PropertyOption(name = RANDOM_ID_PROVIDER_VALUE, value = RANDOM_ID_PROVIDER_VALUE),
            @PropertyOption(name = UUID_PROVIDER_VALUE, value = UUID_PROVIDER_VALUE)}, value = UUID_PROVIDER_VALUE)
    private static final String SESSION_ID_PROVIDER = "session.id";

    @Property(label = "Cookie context path", description = "Used to configure context path of the cookie.", value = DEFAULT_CONTEXT_PATH)
    // TODO: set different cookie for different context path
    private static final String COOKIE_CONTEXT_PATH_PARAMETER = "session.cookie.contextPath";

    @Property(label = "Secure cookie", description = "Used to specify that cookie should be marked as secure. "
            + "Secure cookies are propagated only over HTTPS.", boolValue = false)
    private static final String SECURE_COOKIE_PARAMETER = "session.cookie.secure";

    @Property(label = "HttpOnly cookie", description = "Used to specify that cookie should be marked as HttpOnly. "
            + "Those cookies are not available to javascript.", boolValue = true)
    private static final String COOKIE_HTTP_ONLY_PARAMETER = "session.cookie.httpOnly";

    @Property(label = "Encryption key", description = "Specifies key to be used for encryption. When present activates encryption automatically. "
            + "If key specifies a URL, key will be loaded from specified address. Otherwise it is treated literally.", value = StringUtils.EMPTY)
    private static final String SESSION_ENCRYPTION_KEY = "session.encryption.key";

    @Reference
    private RedisConfigurationService redisConfigurationService;

    private boolean disableSessionManagement;
    private int maxInactiveInterval;
    private boolean sticky;
    private String namespace;
    private boolean timestampSufix;
    private String node;
    private String sessionIdName;
    private int sessionIdLength;
    private boolean delegateWriter;
    // This is not configurable for now and always defaulted to In-Memory
    // implementation. This configuration has been added for future upgrade
    private String repositoryFactory;
    private String sessionTracking;
    private boolean replicationTrigger;
    private String sessionIdProvider;
    private String cookieContextPath;
    private boolean secureCookie;
    private boolean httpOnly;
    private boolean usingEncryption;
    private String encryptionKey;

    @Activate
    protected void onActivation(Map<String, ?> properties) {
        LOGGER.debug("Activation started");

        disableSessionManagement = PropertiesUtil.toBoolean(properties.get(DISABLED_SESSION), true);
        maxInactiveInterval = PropertiesUtil.toInteger(properties.get(SESSION_TIMEOUT), DEFAULT_SESSION_TIMEOUT);
        sticky = PropertiesUtil.toBoolean(properties.get(STICKY_SESSIONS), false);
        namespace = PropertiesUtil.toString(properties.get(SESSION_NAMESPACE), DEFAULT_SESSION_NAMESPACE);
        timestampSufix = PropertiesUtil.toBoolean(properties.get(SESSION_TIMESTAMP), false);
        node = getNodeName(PropertiesUtil.toString(properties.get(SESSION_HOST), StringUtils.EMPTY));
        sessionIdName = PropertiesUtil.toString(properties.get(SESSION_ID_NAME), DEFAULT_SESSION_ID_NAME);
        sessionIdLength = PropertiesUtil.toInteger(properties.get(SESSION_ID_LENGTH), DEFAULT_SESSION_ID_LENGTH);
        delegateWriter = PropertiesUtil.toBoolean(properties.get(DELEGATE_WRITER), false);

        repositoryFactory = redisConfigurationService.isRedisEnabled() ? 
                PropertiesUtil.toString(properties.get(REPOSITORY_FACTORY_NAME), REDIS_REPOSITORY_VALUE) : 
                    IN_MEMORY_REPOSITORY_VALUE;

        sessionTracking = PropertiesUtil.toString(properties.get(SESSION_PROPAGATOR_NAME), COOKIE_SESSION_PROPAGATION_TYPE_VALUE);
        replicationTrigger = Boolean.parseBoolean(PropertiesUtil.toString(properties.get(SESSION_REPLICATION_TRIGGER), "true"));
        sessionIdProvider = PropertiesUtil.toString(properties.get(SESSION_ID_PROVIDER), UUID_PROVIDER_VALUE);
        cookieContextPath = PropertiesUtil.toString(properties.get(COOKIE_CONTEXT_PATH_PARAMETER), DEFAULT_CONTEXT_PATH);
        secureCookie = PropertiesUtil.toBoolean(properties.get(SECURE_COOKIE_PARAMETER), true);
        httpOnly = PropertiesUtil.toBoolean(properties.get(COOKIE_HTTP_ONLY_PARAMETER), true);

        setEncryptionKey(PropertiesUtil.toString(properties.get(SESSION_ENCRYPTION_KEY), StringUtils.EMPTY));
        LOGGER.debug("Configuration details: {}", toString());
    }

    @Modified
    protected void onModification(Map<String, ?> properties) {
        this.onActivation(properties);
    }

    @Deactivate
    protected void onDeactivation(Map<String, ?> properties) {

    }

    @Override
    public boolean isDisableSessionManagement() {
        return disableSessionManagement;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public boolean isSticky() {
        return sticky;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean isTimestampSufix() {
        return timestampSufix;
    }

    @Override
    public String getNode() {
        return node;
    }

    @Override
    public String getSessionIdName() {
        return sessionIdName;
    }

    @Override
    public int getSessionIdLength() {
        return sessionIdLength;
    }

    @Override
    public boolean isDelegateWriter() {
        return delegateWriter;
    }

    @Override
    public String getRepositoryFactory() {
        return repositoryFactory;
    }

    @Override
    public String getSessionTracking() {
        return sessionTracking;
    }

    @Override
    public boolean isReplicationTrigger() {
        return replicationTrigger;
    }

    @Override
    public String getSessionIdProvider() {
        return sessionIdProvider;
    }

    @Override
    public boolean isUsingEncryption() {
        return usingEncryption;
    }

    @Override
    public String getEncryptionKey() {
        return encryptionKey;
    }

    @Override
    public String getCookieContextPath() {
        return cookieContextPath;
    }

    @Override
    public boolean isSecureCookie() {
        return secureCookie;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    private void setEncryptionKey(String keyPath) {
        usingEncryption = StringUtils.isNotBlank(keyPath);
        encryptionKey = usingEncryption ? getEncryptionKey(keyPath) : StringUtils.EMPTY;
    }

    /**
     * Initializes node id. Node id is read either from property, or from
     * environment variables depending on OS.
     *
     * @return node id
     */
    private static String getNodeName(String nodeName) {
        String node = StringUtils.defaultString(nodeName, UNKNOWN_NODE_NAME);

        // On Windows try the 'COMPUTERNAME' variable
        try {
            String osName = System.getProperty("os.name", StringUtils.EMPTY);

            if (StringUtils.startsWithIgnoreCase(osName, "Windows")) {
                node = System.getenv("COMPUTERNAME");
            } else {
                node = System.getenv("HOSTNAME");
            }
        } catch (SecurityException e) {
            LOGGER.info("Security exception when trying to get environmnet variable", e);
        }

        if (StringUtils.isEmpty(node)) {
            // Try portable way
            try {
                node = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                LOGGER.info("Unable to resolve local host, that's a strange error, but somehow it occured.", e);
            }
        }

        return node;
    }

    /**
     * Returns encryption key to use. If encryption is disabled, returns
     * <code>null</code>.
     *
     * @return the encryption key
     */
    private static String getEncryptionKey(String keyPath) {
        try {
            URL url = new URL(keyPath);

            if (allowedProtocol(url.getProtocol())) {
                return loadKeyFromUrl(url);
            }

            throw new IllegalStateException(
                    "Unknown protocol in url `" + url + "`. Supported protocols are file, http and https.");
        } catch (Exception e) { // NOSONAR Ignore
            // When exception occurs, key is not URL
            LOGGER.error("Key was not provided via url.", e);
        }

        // Key is not specified by filepath or URL.
        // So assuming the key is given in the configuration as is.
        return keyPath;
    }

    /**
     * Loads encryption key from specified URL.
     *
     * @param url
     *            from which to load the key
     * @return the loaded encyption key
     */
    private static String loadKeyFromUrl(URL url) {
        try (InputStream is = url.openStream(); Scanner s = new Scanner(is)) {
            s.useDelimiter("\\A");

            if (s.hasNext()) {
                LOGGER.info("Loaded ecnryption key from url `{}`", url);
                return s.next();
            }

            throw new IllegalStateException("Unable to load key from url `" + url + "`. Destination was empty.");
        } catch (Exception e) {
            LOGGER.error("Unable to load key from url `" + url + "`.", e);
            return null;
        }
    }

    private static boolean allowedProtocol(String protocol) {
        return Arrays.asList(ALLOWED_PROTOCOL_FOR_ENCRYPTION_KEY).contains(protocol);
    }

    @Override
    public String toString() {
        return String.format(
                "SessionConfiguration [redisConfigurationService=%s, disableSessionManagement=%s, maxInactiveInterval=%s, sticky=%s, namespace=%s, "
                + "timestampSufix=%s, node=%s, sessionIdName=%s, sessionIdLength=%s, delegateWriter=%s, repositoryFactory=%s, sessionTracking=%s, "
                + "replicationTrigger=%s, sessionIdProvider=%s, cookieContextPath=%s, secureCookie=%s, httpOnly=%s, usingEncryption=%s, encryptionKey=%s]",
                redisConfigurationService, disableSessionManagement, maxInactiveInterval, sticky, namespace,
                timestampSufix, node, sessionIdName, sessionIdLength, delegateWriter, repositoryFactory,
                sessionTracking, replicationTrigger, sessionIdProvider, cookieContextPath, secureCookie, httpOnly,
                usingEncryption, encryptionKey);
    }
}
