package com.test.session.servlet;

import java.util.EventListener;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionEvent;
//import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionManager;
import com.test.session.api.SessionRepository;
import com.test.session.api.SessionTracking;
import com.test.session.models.SessionConstants;
import com.test.session.servlet.filters.SessionManagementFilter;

/**
 * This class contains various methods that are called either from session
 * enabled filters, or from code injected by <code>SessionAgent</code>.
 */
public final class SessionHelpers {
    private static final Logger logger = LoggerFactory.getLogger(SessionHelpers.class);

    /**
     * This method initializes session management for a given
     * {@link ServletContext}. This method is called from
     * {@link SessionManagementFilter#init(javax.servlet.FilterConfig)}. The method will
     * create and configure {@link SessionManager} if needed.
     *
     * @param servletContext
     *            the active servlet context
     * @return list of method handles for publicly accessible methods
     * @throws InvalidSyntaxException 
     *
     */
    public void initSessionManagement(ServletContext servletContext) throws InvalidSyntaxException {
        servletContext.setAttribute(SessionConstants.SESSION_HELPERS, this);
        // ServletContextDescriptor scd = getDescriptor(servletContext);
        //setupContext(servletContext);
//        SessionNotifier notifier = new HttpSessionNotifier(scd);
        // SessionFactory factory = new HttpSessionFactory(servletContext);

        SessionConfigurationService conf = getInstanceFromOsGi(this.getClass(), SessionConfigurationService.class);
        SessionRepository repository = getInstanceFromOsGi(conf.getRepositoryFactory(), this.getClass(), SessionRepository.class);
        SessionTracking tracking = getInstanceFromOsGi(conf.getSessionTracking(), this.getClass(), SessionTracking.class);

//        ExecutorFacade executors = new ExecutorFacade(conf);

//        SessionManager sessionManagement = new SessionManager(executors, factory, repository, tracking, notifier, conf, classLoader);
//        servletContext.setAttribute(Attributes.SESSION_MANAGER, sessionManagement);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInstanceFromOsGi(Class<?> currentClass, Class<T> instanceFor) throws InvalidSyntaxException {
        BundleContext bundleContext = FrameworkUtil.getBundle(currentClass).getBundleContext();
        @SuppressWarnings("rawtypes")
        ServiceReference serviceReference = bundleContext.getServiceReference(instanceFor.getName());

        if (serviceReference != null) {
            return (T) bundleContext.getService(serviceReference);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInstanceFromOsGi(String filter, Class<?> currentClass, Class<T> instanceFor) throws InvalidSyntaxException {
        if (StringUtils.isBlank(filter)) {
            return getInstanceFromOsGi(currentClass, instanceFor);
        }

        filter = "(service.pid=" + filter + ")";

        BundleContext bundleContext = FrameworkUtil.getBundle(currentClass).getBundleContext();
        @SuppressWarnings("rawtypes")
        ServiceReference[] allServiceReferences = bundleContext.getServiceReferences(instanceFor.getName(), filter);

        if (allServiceReferences != null && allServiceReferences.length > 0) {
            return (T) bundleContext.getService(allServiceReferences[0]);
        }

        return null;
    }

    /**
     * This method is called inside Servlet 2.5 containers to collect
     * information about existing HttpServletListeners.
     *
     * @param context
     *            current servlet context
     * @param request
     *            current servlet request
     */
    static void findListenersByIntercepting(ServletContext context, HttpServletRequest request) {
        if (context.getAttribute(SessionConstants.INTROSPECTING_LISTENERS) == null) {
            // If we haven't started or completed introspecting listeners, let's
            // do it
            logger.info("Started collecting servlet listeners.");
            // We put a Set that will contain all introspected listeners in
            // servlet
            // context attribute
            context.setAttribute(SessionConstants.INTROSPECTING_LISTENERS, new HashSet<Object>());
            // Then we create session inside container (not "our" session).
            // This should trigger all HttpSessionListeners
            HttpSession session = request.getSession();
            // Next we add attribute. This should trigger all
            // HttpSessionAttributeListeners
            session.setAttribute(SessionConstants.DUMMY_ATTRIBUTE, SessionConstants.DUMMY_ATTRIBUTE);
            session.removeAttribute(SessionConstants.DUMMY_ATTRIBUTE);
            // Finally we remove this session.
            session.invalidate();
            // And we mark that introspecting was done.
            context.setAttribute(SessionConstants.INTROSPECTING_LISTENERS, Boolean.TRUE);
            logger.info("Finished collecting listeners.");
        }
    }

    /**
     * Call to this method is injected by agent into implementations of
     * {@link HttpSessionAttributeListener} and {@link HttpSessionListener}
     * inside Servlet 2.5 containers. It's roll is to collect session listeners
     * so they can be invoked by the library when it manages sessions.
     *
     * @param caller
     *            listener where event was received
     * @param event
     *            event that was received
     */
    @SuppressWarnings("unchecked")
    public void interceptHttpListener(EventListener caller, HttpSessionEvent event) {
        if (event.getSession() instanceof RepositoryBackedHttpSession) {
            return;
        }
        Object value = event.getSession().getServletContext().getAttribute("INTROSPECTING_LISTENERS");
        if (value != null && !((Set<?>) value).contains(caller)) {
            ((Set<Object>) value).add(caller);
            onAddListener(event.getSession().getServletContext(), caller);
        }
    }

    /**
     * This method retrieves {@link ServletContextDescriptor} for a
     * {@link ServletContext} from registry, or if it {@link ServletContext}
     * isn't registered, adds it to the registry with empty
     * {@link ServletContextDescriptor}
     *
     * @param servletContext
     *            the active servlet context
     * @return descriptor from registry
     */
//    private ServletContextDescriptor getDescriptor(ServletContext servletContext) {
//        ServletContextDescriptor scd = (ServletContextDescriptor) servletContext
//                .getAttribute(Attributes.SERVLET_CONTEXT_DESCRIPTOR);
//        if (scd == null) {
//            scd = new ServletContextDescriptor(servletContext);
//            servletContext.setAttribute(Attributes.SERVLET_CONTEXT_DESCRIPTOR, scd);
//            logger.info("Registered servlet context {}.", servletContext.getContextPath());
//        }
//        return scd;
//    }

    /**
     * This method is used by injected code to register listeners for
     * {@link ServletContext}. If object argument is a {@link ServletContext}
     * and listener argument contains {@link HttpSessionListener} or
     * {@link HttpSessionAttributeListener}, the method will add them to list of
     * known listeners associated to {@link ServletContext}
     *
     * @param servletContext
     *            the active servlet context
     * @param listener
     *            the listener to use
     */
    public void onAddListener(ServletContext servletContext, Object listener) {
        String contextPath = servletContext.getContextPath();
        // ServletContextDescriptor scd = getDescriptor(servletContext);
        logger.debug("Registering listener {} for context {}", listener, contextPath);
        // As theoretically one class can implement many listener interfaces we
        // check if it implements each of supported ones
        if (listener instanceof HttpSessionListener) {
            // scd.addHttpSessionListener((HttpSessionListener) listener);
        }
        if (listener instanceof HttpSessionAttributeListener) {
            // scd.addHttpSessionAttributeListener((HttpSessionAttributeListener) listener);
        }
        if (1 == 1) {
            // Guard the code inside block to avoid use of classes
            // that are not available in versions before Servlet 3.1
            // if (listener instanceof HttpSessionIdListener) { // NOSONAR
            // scd.addHttpSessionIdListener((HttpSessionIdListener)listener);
            // }
        }
    }

    /**
     * Sets up servlet context - registers {@link SessionManagementFilter} and
     * {@link ShutdownListener}.
     *
     * @param context
     *            servlet context to set up
     */
//    static void setupContext(ServletContext context) {
//        if (2 == 2) {
//            // When using Servlet 3.x+, we will register SessionFilter to make
//            // sure session replacement is enabled
//            Dynamic reg = context.addFilter("com.amdeus.session.filter", new SessionManagementFilter());
//            if (reg != null) {
//                // The filter applies to all requests
//                reg.addMappingForUrlPatterns(null, false, "/*");
//            }
//            // At the web app shutdown, we need to do some cleanup
//            // context.addListener(new ShutdownListener());
//        }
//    }
}
