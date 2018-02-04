package com.test.session.models;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import com.test.session.configuration.SessionConfiguration;
import com.test.session.idprovider.RandomIdProvider;
import com.test.session.servlet.SessionHelpers;
import com.test.session.servlet.wrappers.HttpRequestWrapper;

public interface SessionConstants {
    /**
     * Returns true if the Servlet 3 APIs are detected.
     */
    static boolean IS_SERVLET_3() {
        try {
            ServletRequest.class.getMethod("startAsync");
            return true;
        } catch (NoSuchMethodException e) { // NOSONAR no method, so no servlet 3
            return false;
        }
    }

    /**
     * Returns true if the Servlet 3.1 APIs are detected.
     */
    static boolean IS_SERVLET_3_1() {
        try {
            HttpServletRequest.class.getMethod("changeSessionId");
            return true;
        } catch (NoSuchMethodException e) { // NOSONAR no method, so no servlet 3.1
            return false;
        }
    }

    /**
     * Default session namespace is "default"
     */
    String DEFAULT_SESSION_NAMESPACE = "default";

    int DEFAULT_SESSION_TIMEOUT = 1800;

    /**
     * Default name for the cookie or URL element.
     */
    String DEFAULT_SESSION_ID_NAME = "JSESSIONID";

    /**
     * Default session id length when using {@link RandomIdProvider} is 30
     * bytes.
     */
    int DEFAULT_SESSION_ID_LENGTH = 30;

    String UNKNOWN_NODE_NAME = "unknown";

    /**
     * Use cookie based session tracking (session id is stored in cookie).
     */
    String COOKIE_SESSION_PROPAGATION_TYPE = "Cookie Based";

    String COOKIE_SESSION_PROPAGATION_TYPE_VALUE = "cookiePropagation";

    /**
     * Use url based session tracking (session id is appended to URL).
     */
    String URL_SESSION_PROPAGATION_TYPE = "URL Based";

    String URL_SESSION_PROPAGATION_TYPE_VALUE = "urlPropagation";

    /**
     * Use url based session tracking (session id is appended to header).
     */
    // TODO: This is still in development and will be available with next version
    String HEADER_SESSION_PROPAGATION_TYPE = "Header Based";

    String HEADER_SESSION_PROPAGATION_TYPE_VALUE = "headerPropagation";

    String IN_MEMORY_REPOSITORY_NAME = "In-Memory Repository";

    String IN_MEMORY_REPOSITORY_VALUE = "inmemoryRepository";

    String REDIS_REPOSITORY_NAME = "Redis Repository";

    String REDIS_REPOSITORY_VALUE = "redisRepository";

    /**
     * Session data is replicated on set of the attribute and when an attribute
     * retrieved via Attribute contains a non-primitive type. This means that
     * the get of an attribute of the well-known Java type such as Boolean,
     * Character, Number (Double, Float, Integer, Long), doesn't trigger
     * replication to repository, but getAttribute operation for other types of
     * attribute will trigger update in the repository.
     */
    String SET_AND_NON_PRIMITIVE_GET = "Set and non primitive get";

    /**
     * This option assumes that the application will explicitly call
     * setAttribute on the session when the data needs to be replicated. It
     * prevents unnecessary replication and can benefit overall performance, but
     * is inherently unsafe as attributes that were changed after the get, but
     * where never
     */
    String SET = "Set";

    String RANDOM_ID_PROVIDER_NAME = "Random ID Provider";

    String RANDOM_ID_PROVIDER_VALUE = "randomIdProvider";

    String UUID_PROVIDER_NAME = "UUID Provider";

    String UUID_PROVIDER_VALUE = "uuidProvider";

    String[] ALLOWED_PROTOCOL_FOR_ENCRYPTION_KEY = {"http", "https", "file"};

    String DEFAULT_CONTEXT_PATH = "/";
    
    String DUMMY_ATTRIBUTE = "com.test.session.dummy";
    String SESSION_HELPER_METHODS = "com.test.session.servlet.SessionHelpers.methods";
    String DEFAULT_REPOSITORY_FACTORY = "com.test.session.repository.inmemory.InMemoryRepositoryFactory";
    String INTROSPECTING_LISTENERS = "com.test.session.introspected.listeners";
    String ALREADY_FILTERED_ATTRIBUTE = "com.test.session.already-filtered";
    String SESSION_PROPAGATED = "com.test.session.sessionPropagated";
    String REQUEST_WRAPPED_ATTRIBUTE = HttpRequestWrapper.class.getName();
    String SESSION_CONFIGURATION = SessionConfiguration.class.getName();
    String SESSION_HELPERS = SessionHelpers.class.getName();
}
