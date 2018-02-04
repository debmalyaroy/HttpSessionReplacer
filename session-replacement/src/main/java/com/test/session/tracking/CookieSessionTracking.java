package com.test.session.tracking;

import static org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_MULTIPLE;
import static org.apache.felix.scr.annotations.ReferencePolicy.DYNAMIC;

import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;

import com.test.session.api.OSGiDependencyService;
import com.test.session.api.RequestWithSession;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionIdProvider;
import com.test.session.api.SessionTracking;
import com.test.session.configuration.SessionConfiguration;
import com.test.session.models.SessionConstants;

/**
 * This class propagates session using HTTP cookies.
 * <p>
 * In case of HTTPS request, cookie is marked as secure. For Servlet 3.x and
 * later containers, cookie is marked as HTTP only.
 * <p>
 * Cookie applies only on the context path of the {@link ServletContext}. I.e.
 * it is only send for URL that are prefixed by context path.
 * <p>
 * The cookie expiration is set only if session has expired, and the value of
 * expiration is 0 (i.e. immediately).
 * <p>
 * The cookie name is decided in order of priority:
 * <ul>
 * <li>using {@link SessionConfiguration#SESSION_ID_NAME} {@link ServletContext}
 * initialization parameter
 * <li>using {@link SessionConfiguration#SESSION_ID_NAME} system property
 * <li>value of {@link SessionConfiguration#DEFAULT_SESSION_ID_NAME}
 * </ul>
 *
 * ServletContext initialization parameters can be used to configure:
 * <ul>
 * <li>if the path of the path of the cookie is using context path or root
 * (com.test.session.cookie.contextPath set to true to use context path). By
 * default cookie applies to root path.
 * <li>using http only cookies (controlled by com.test.session.cookie.httpOnly).
 * By default cookies are httpOnly.
 * </ul>
 *
 * the path of the cookie:
 *
 */
@Component(name = SessionConstants.COOKIE_SESSION_PROPAGATION_TYPE_VALUE, immediate = true)
@Service(SessionTracking.class)
@References({
    @Reference(referenceInterface = SessionIdProvider.class, policy = DYNAMIC, cardinality = MANDATORY_MULTIPLE)
})
public class CookieSessionTracking extends BaseSessionTracking {

    @SuppressWarnings("unused")
    private final static String HTTP_ONLY_COMMENT = "__HTTP_ONLY__"; // Jetty way to set a cookie as httpOnly

    @Reference
    private SessionConfigurationService sessionConfigurationService;

    protected final void bindSessionIdProvider(final SessionIdProvider service, final Map<Object, Object> props) {
        sessionIdProviders.put(OSGiDependencyService.getProperty(props), service);
    }

    protected final void unbindSessionIdProvider(final SessionIdProvider service, final Map<Object, Object> props) {
        sessionIdProviders.remove(OSGiDependencyService.getProperty(props));
    }

    @Override
    public String retrieveId(RequestWithSession request) {
        LOGGER.debug("Retrieving existing session ID.");

        String sessionIdCookieName = sessionConfigurationService.getSessionIdName();
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();

        if (ArrayUtils.isNotEmpty(cookies)) {
            for (Cookie cookie : cookies) {
                LOGGER.debug("Processing cookie {}", getCookieAsString(cookie));

                if (StringUtils.equals(sessionIdCookieName, cookie.getName())) {
                    LOGGER.debug("Got the session cookie");
                    return clean(cookie.getValue());
                }
            }
        }

        return null;
    }

    @Override
    public void propagateSession(String sessionId, HttpServletResponse response) {
        LOGGER.debug("Propagating session.");

        Cookie cookie = new Cookie(sessionConfigurationService.getSessionIdName(), StringUtils.isBlank(sessionId) ? null : sessionId);

        // Set it as session cookie
        cookie.setMaxAge(StringUtils.isBlank(sessionId) ? 0 : -1);
        cookie.setHttpOnly(sessionConfigurationService.isHttpOnly());
        cookie.setSecure(sessionConfigurationService.isSecureCookie());
        cookie.setPath(StringUtils.defaultString(sessionConfigurationService.getCookieContextPath(), SessionConstants.DEFAULT_CONTEXT_PATH));

        LOGGER.debug("Propagating session with cookie {}", getCookieAsString(cookie));
        response.addCookie(cookie);
    }

    @Override
    protected boolean appendTimeStamp() {
        return sessionConfigurationService.isTimestampSufix();
    }

    @Override
    protected String getProviderName() {
        return sessionConfigurationService.getSessionIdProvider();
    }

    private static String getCookieAsString(Cookie cookie) {
        return new StringBuffer("Cookie = {")
                .append(cookie.getName())
                .append(";")
                .append(cookie.getValue())
                .append(";")
                .append(cookie.getComment())
                .append(";")
                .append(cookie.getDomain())
                .append(";")
                .append(cookie.getMaxAge())
                .append(";")
                .append(cookie.getPath())
                .append(";")
                .append(cookie.getSecure())
                .append(";")
                .append(cookie.getVersion())
                .append(";")
                .append(cookie.isHttpOnly())
                .append("}")
                .toString();
    }
}
