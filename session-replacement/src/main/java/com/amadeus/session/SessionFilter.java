package com.amadeus.session;

import static java.lang.Integer.MIN_VALUE;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;

import com.amadeus.session.api.HttpRequestResponseProcessor;

/**
 * Filter that wraps the httpRequest to enable Http Session caching.
 *
 * Note that it won't wrap the request twice even if the filter is called two
 * times.
 *
 * @see ServletRequestWrapper
 */

/**
 * "Felix" Filters are different from Sling Filters, in that they are executed
 * by Apache Felix before the Sling Engine is engaged.
 *
 * This allows for: - Processing a request before Sling Authentication and
 * Resource Resolution has occurs.
 *
 * This prevents: - Understanding accessing any Sling Context; Permissions,
 * Resource Resolution, etc.
 *
 */
// A major difference from Sling Filters is Servlet Filters can be registered
// via the Felix HTTP Whiteboard to URL path patterns.
// The filter.order is used to determine the order of Felix Servlet Filters; the
// Smaller the number, the earlier it will be invoked.
// - Registered Felix Servlet Filters in AEM can be viewed her:
// http://localhost:4502/system/console/status-httpwhiteboard
@Component(immediate = true)
@Service
@Properties({ @Property(name = "pattern", value = "/content/samples.*", propertyPrivate = true),
        @Property(name = "filter.order", intValue = MIN_VALUE, propertyPrivate = true) })
public class SessionFilter implements Filter {

    @Reference
    private HttpRequestResponseProcessor httpRequestResponseProcessor;

    ServletContext servletContext;

    /**
     * Initializes session management based on repository for current servlet
     * context.
     *
     * @param config
     *            The filter configuration.
     */
    @Override
    public void init(FilterConfig config) {
        initForSession(config);
    }

    /**
     * Initializes session management based on repository for current servlet
     * context. This method is internal method for session management.
     *
     * @param config
     *            The filter configuration.
     */
    public void initForSession(FilterConfig config) {
        if (servletContext == null) {
            servletContext = config.getServletContext();
            httpRequestResponseProcessor.initSessionManagement(servletContext);
        }
    }

    /**
     * Implements wrapping of HTTP request and enables handling of sessions
     * based on repository.
     *
     * @param originalRequest
     *            The request to wrap
     * @param originalResponse
     *            The response.
     * @param chain
     *            The filter chain.
     * @throws IOException
     *             If such exception occurs in chained filters.
     * @throws ServletException
     *             If such exception occurs in chained filters.
     */
    @Override
    public void doFilter(ServletRequest originalRequest, ServletResponse originalResponse, FilterChain chain) throws IOException, ServletException {
        // Since this context is that of a Felix HTTP Servlet Filter, we are
        // guaranteed the request and response are HTTP Filters.
        HttpServletRequest request = (HttpServletRequest) originalRequest;
        HttpServletResponse response = (HttpServletResponse) originalResponse;

        // Do work before sending the request down the Felix Filter AND Sling processing chain...
        httpRequestResponseProcessor.prepareRequest(request, response, servletContext);
        httpRequestResponseProcessor.prepareResponse(request, response, servletContext);

        // The Request/Response have now been fully processed by Sling/AEM and coming out the other side of the Felix filter chain
        
        try {
            // Call next filter in chain
            chain.doFilter(request, response);
        } finally {
            // Commit the session. Implementation expects that request has been
            // wrapped and that originalRequest is not an
            // OffloadSessionHttpServletRequest
            httpRequestResponseProcessor.commitRequest(request, originalRequest, servletContext);
        }
    }

    /**
     * No specific processing is done when this filter is being taken out of
     * service.
     */
    @Override
    public void destroy() {
        // Do nothing
    }
}