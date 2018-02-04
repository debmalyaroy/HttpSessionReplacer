package com.test.session.configuration;

import static org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_MULTIPLE;
import static org.apache.felix.scr.annotations.ReferencePolicy.DYNAMIC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;

import com.test.session.api.OSGiDependencyService;
import com.test.session.api.RedisConfigurationService;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionTracking;
import com.test.session.connection.api.RedisConnector;
import com.test.session.models.RedisConstants;

@Component(immediate = true)
@Service
@References({
    @Reference(referenceInterface = SessionTracking.class, policy = DYNAMIC, cardinality = MANDATORY_MULTIPLE),
    @Reference(referenceInterface = RedisConnector.class, policy = DYNAMIC, cardinality = MANDATORY_MULTIPLE)
})
public class OSGiDependencyServiceImpl implements OSGiDependencyService {

    @Reference
    private SessionConfigurationService configurationService;

    @Reference
    private RedisConfigurationService redisConfigurationService;

    private Map<String, SessionTracking> sessionTrackings = new HashMap<>();
    private Map<String, RedisConnector> redisConnectors = new HashMap<>();

    protected final void bindSessionTracking(final SessionTracking service, final Map<Object, Object> props) {
        sessionTrackings.put(OSGiDependencyService.getProperty(props), service);
    }

    protected final void unbindSessionTracking(final SessionTracking service, final Map<Object, Object> props) {
        sessionTrackings.remove(OSGiDependencyService.getProperty(props));
    }

    protected final void bindRedisConnector(final RedisConnector service, final Map<Object, Object> props) {
        redisConnectors.put(OSGiDependencyService.getProperty(props), service);
    }

    protected final void unbindRedisConnector(final RedisConnector service, final Map<Object, Object> props) {
        redisConnectors.remove(OSGiDependencyService.getProperty(props));
    }

    @Override
    public SessionTracking getSessionTrackingMethod() {
        return Optional.ofNullable(sessionTrackings.get(configurationService.getSessionTracking()))
                .orElseThrow(() -> new IllegalArgumentException("No Session Tracking method with name " + configurationService.getSessionTracking() + " is configured."));
    }

    @Override
    public RedisConnector getRedisConnector() {
        if (!redisConfigurationService.isRedisEnabled()) {
            return null;
        }

        String clusterMode = redisConfigurationService.getClusterMode();

        switch (clusterMode) {
            case RedisConstants.REDIS_MODE_SINGLE:
            case RedisConstants.REDIS_MODE_SENTINEL:
                return redisConnectors.get(RedisConstants.JEDIS_POOL_CONNECTOR_PID);
            case RedisConstants.REDIS_MODE_CLUSTER:
                return redisConnectors.get(RedisConstants.JEDIS_CLUSTER_CONNECTOR_PID);
            default:
                throw new IllegalArgumentException("Unsupported redis mode: " + clusterMode);
        }
    }
}
