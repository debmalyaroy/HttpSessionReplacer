package com.test.session.tracking;

import static org.apache.felix.scr.annotations.ReferenceCardinality.MANDATORY_MULTIPLE;
import static org.apache.felix.scr.annotations.ReferencePolicy.DYNAMIC;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

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
 * This class propagates session id using URL re-writing. The session is
 * appended to path element of the URL and the format of the session element is
 * <code>;&lt;id-name&gt;=&lt;sessionId&gt;</code>. The <code>id-name</code> is
 * specified in order of priority:
 * <ul>
 * <li>using {@link SessionConfiguration#SESSION_ID_NAME} {@link ServletContext}
 * initialization parameter
 * <li>using {@link SessionConfiguration#SESSION_ID_NAME} system property
 * <li>value of {@link SessionConfiguration#DEFAULT_SESSION_ID_NAME}
 * </ul>
 */
@Component(name = SessionConstants.URL_SESSION_PROPAGATION_TYPE_VALUE, immediate = true)
@Service(SessionTracking.class)
@References({
    @Reference(referenceInterface = SessionIdProvider.class, policy = DYNAMIC, cardinality = MANDATORY_MULTIPLE)
})
public class UrlSessionTracking extends BaseSessionTracking {
    private static final String SESSION_ID_URL_PATTERN = ";%s=";

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
        LOGGER.debug("Retrieving session ID from URL.");

        String sessionIdPathItem = String.format(SESSION_ID_URL_PATTERN, sessionConfigurationService.getSessionIdName());
        String requestUri = ((HttpServletRequest) request).getRequestURI();
        int sessionIdStart = StringUtils.lastIndexOf(requestUri, sessionIdPathItem);

        LOGGER.debug("Session ID path param {} and the request URI is {}", sessionIdPathItem, requestUri);

        if (sessionIdStart != -1) {
            sessionIdStart += sessionIdPathItem.length();

            LOGGER.debug("Cleaning up the session ID. It is available {} characters from the start in requestURL.", sessionIdStart);
            return clean(StringUtils.substring(requestUri, sessionIdStart));
        }

        LOGGER.debug("Session ID not found in the URL.");
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public String encodeUrl(String sessionId, String url) {
        LOGGER.debug("Encoding the URL {} with session ID {}", url, sessionId);

        String sessionIdPathItem = String.format(SESSION_ID_URL_PATTERN, sessionConfigurationService.getSessionIdName());
        String encodedSessionAlias = StringUtils.EMPTY;

        try {
            encodedSessionAlias = URLEncoder.encode(sessionId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            encodedSessionAlias = URLEncoder.encode(sessionId);
        }

        int queryStart = StringUtils.indexOf(url, "?");
        LOGGER.debug("First index of '?' in the URL {}", queryStart);

        if (queryStart < 0) {
            LOGGER.debug("No query has been found. Returning the URL {}", (url + sessionIdPathItem + encodedSessionAlias));
            return url + sessionIdPathItem + encodedSessionAlias;
        }

        String path = StringUtils.substring(url, 0, queryStart);
        String query = StringUtils.substring(url, queryStart + 1, url.length());
        path += sessionIdPathItem + encodedSessionAlias;

        LOGGER.debug("Encoded URL {}", (path + "?" + query));
        return path + '?' + query;
    }

    @Override
    protected String getProviderName() {
        return sessionConfigurationService.getSessionIdProvider();
    }

    @Override
    protected boolean appendTimeStamp() {
        return sessionConfigurationService.isTimestampSufix();
    }
}
