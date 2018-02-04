package com.test.session.api;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.osgi.PropertiesUtil;

import com.test.session.connection.api.RedisConnector;

public interface OSGiDependencyService {
    static String getProperty(final Map<Object, ?> props) {
        return PropertiesUtil.toString(props.get("service.pid"), StringUtils.EMPTY);
    }

    SessionTracking getSessionTrackingMethod();

    RedisConnector getRedisConnector();
}
