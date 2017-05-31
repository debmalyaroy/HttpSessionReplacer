package com.amadeus.session.api;


import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public interface HttpRequestResponseProcessor {
    String DISABLED_SESSION = "com.amadeus.session.disabled";

    /**
     * This method is called from {@link Filter}
     * implementations modified by SessionAgent. The method wraps
     * {@link ServletRequest} in {@link HttpRequestWrapper}.
     * <p>
     * The method will wrap request at most once per request and will only wrap
     * instances of {@link HttpServletRequest}.
     *
     * @param request
     *            request received by filter
     * @param response
     *            response received by filter
     * @param filterContext
     *            {@link ServletContext} used when filter was initialized
     * @return wrapped or original request
     */
    ServletRequest prepareRequest(HttpServletRequest request, HttpServletResponse response, ServletContext filterContext);

    /**
     * This method is called from {@link SessionFilter} or from {@link Filter}
     * implementations modified by SessionAgent. The method retrieves response
     * stored in {@link HttpRequestWrapper}.
     *
     * @param request
     *            request received by filter
     * @param response
     *            response received by filter
     * @param filterContext
     *            servlet context of the filter
     * @return wrapped or original response
     */
    ServletResponse prepareResponse(HttpServletRequest request, HttpServletResponse response, ServletContext filterContext);

    /**
     * This method initializes session management for a given
     * {@link ServletContext}. This method is called from
     * {@link SessionFilter#init(javax.servlet.FilterConfig)}.
     *
     * @param servletContext
     *            the servlet context where filter is registered
     *
     */
    void initSessionManagement(ServletContext servletContext);

    /**
     * Commits request and stores session in repository. This method is called
     * from the filter. The commit is only done if the filter is the one that
     * wrapped the request into HttpRequestWrapper.
     * <p>
     * The logic to check if the caller filter is the one that wrapped request
     * is based on requirement that original request and the one used by filter
     * are different and that original request is not
     * {@link HttpRequestWrapper}.
     *
     * @param request
     *            potentially wrapped request
     * @param oldRequest
     *            original request received by filter
     * @param filterContext
     *            servlet context of the filter
     */
    void commitRequest(ServletRequest request, ServletRequest oldRequest, ServletContext filterContext);

    /**
     * Call to this method is injected by agent into implementations of
     * {@link HttpSessionAttributeListener} and {@link HttpSessionListener}
     * inside Servlet 2.5 containers. It's roll is to collect session listeners
     * so they can be invoked by the library when it manages sessions.
     *
     * @param listener
     *            listener where event was received
     * @param event
     *            event that was received
     */
    void interceptHttpListener(Object listener, HttpSessionEvent event);

    /**
     * This method is used by injected code to register listeners for
     * {@link ServletContext}. If object argument is a {@link ServletContext}
     * and listener argument contains {@link HttpSessionListener} or
     * {@link HttpSessionAttributeListener}, the method will add them to list of
     * known listeners associated to {@link ServletContext}
     *
     * @param object
     *            the object that should be servlet context
     * @param listener
     *            the listener object
     */
    void onAddListener(Object object, Object listener);
}
