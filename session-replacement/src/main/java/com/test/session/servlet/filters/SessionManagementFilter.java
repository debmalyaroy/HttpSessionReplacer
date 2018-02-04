package com.test.session.servlet.filters;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionManager;
import com.test.session.models.SessionConstants;
import com.test.session.servlet.wrappers.HttpRequestWrapper;
import com.test.session.servlet.wrappers.HttpResponseWrapper;

/**
 * Filter that wraps the httpRequest to enable HTTP Session caching.
 *
 * Note that it won't wrap the request twice even if the filter is called two times.
 *
 * @see ServletRequestWrapper
 */
// A major difference from Sling Filters is Servlet Filters can be registered via the Felix HTTP Whiteboard to URL path patterns.
// The filter.order is used to determine the order of Felix Servlet Filters; the Smaller the number, the earlier it will be invoked.
// - Registered Felix Servlet Filters in AEM can be viewed her: http://localhost:4502/system/console/status-httpwhiteboard
@Component(immediate = true)
@Service
@Properties({
    @Property(name = "pattern", value = "/.*", propertyPrivate = true),
    @Property(name = "service.ranking", intValue = Integer.MIN_VALUE + 9, propertyPrivate = true),
    @Property(name = "filter.order", intValue = -10000, propertyPrivate = true)
})
public class SessionManagementFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManagementFilter.class);

    @Reference
    private SessionConfigurationService sessionConfigurationService;

    @Reference
    private SessionManager sessionManager;

    private ServletContext servletContext;

    @Override
    public void init(FilterConfig config) {
        if (servletContext == null) {
            servletContext = config.getServletContext();
        }
    }

    @Override
    public void doFilter(ServletRequest originalRequest, ServletResponse originalResponse, FilterChain chain) throws IOException, ServletException {
        boolean hasAlreadyFilteredAttribute = originalRequest.getAttribute(SessionConstants.ALREADY_FILTERED_ATTRIBUTE) != null;
        boolean disableCustomSessionManagement = sessionConfigurationService.isDisableSessionManagement();

        LOGGER.debug("Starting custom session management. alreadyFiltered {}, disableSessionManagement {}", hasAlreadyFilteredAttribute, disableCustomSessionManagement);

        if (hasAlreadyFilteredAttribute || disableCustomSessionManagement) {
            LOGGER.debug("Already filtered attribute {} and disable session management {}. Not proceeding with this filter.", hasAlreadyFilteredAttribute, disableCustomSessionManagement);
            // Proceed without invoking this filter...
            chain.doFilter(originalRequest, originalResponse);
        } else {
            // Do invoke this filter...
            originalRequest.setAttribute(SessionConstants.ALREADY_FILTERED_ATTRIBUTE, Boolean.TRUE);

            try {
                HttpServletRequest httpRequest = (HttpServletRequest) originalRequest;
                HttpServletResponse httpResponse = (HttpServletResponse) originalResponse;

                doFilterInternal(httpRequest, httpResponse, chain);
            } finally {
                LOGGER.debug("Filter chain completed. Removing the already filtered attribute.");
                // Remove the "already filtered" request attribute for this request.
                originalRequest.removeAttribute(SessionConstants.ALREADY_FILTERED_ATTRIBUTE);
            }
        }
    }

    @Override
    public void destroy() {
        // Do nothing
    }

    private void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        LOGGER.debug("Wrapping the request and response.");
        sessionManager.setServletContext(servletContext);

        HttpRequestWrapper wrappedRequest = new HttpRequestWrapper(request, sessionManager);
        HttpResponseWrapper wrappedResponse = new HttpResponseWrapper(wrappedRequest, response, sessionConfigurationService.isDelegateWriter());

        wrappedRequest.setResponse(wrappedResponse);

        try {
            LOGGER.debug("Proceeding with other filters.");
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            commitRequest(wrappedRequest);
        }
    }

    private void commitRequest(HttpRequestWrapper request) {
        LOGGER.debug("Committing response.");

        try {
            request.commit();

            if (request.getResponse() != null) {
                request.getResponse().flushBuffer();
            }
        } catch (Exception e) {
            // Recover from any exception and log it
            LOGGER.error("An exception occured while commiting the session.", e);
        }
    }
}
