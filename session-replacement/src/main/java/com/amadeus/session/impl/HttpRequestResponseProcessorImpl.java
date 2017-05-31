package com.amadeus.session.impl;

import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionIdListener;
import javax.servlet.http.HttpSessionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amadeus.session.ExecutorFacade;
import com.amadeus.session.SessionConfiguration;
import com.amadeus.session.SessionFactory;
import com.amadeus.session.SessionFilter;
import com.amadeus.session.SessionManager;
import com.amadeus.session.SessionNotifier;
import com.amadeus.session.SessionRepository;
import com.amadeus.session.SessionRepositoryFactory;
import com.amadeus.session.SessionTracking;
import com.amadeus.session.api.HttpRequestResponseProcessor;
import com.amadeus.session.repository.inmemory.InMemoryRepository;
import com.amadeus.session.servlet.Attributes;
import com.amadeus.session.servlet.HttpSessionFactory;
import com.amadeus.session.servlet.HttpSessionNotifier;
import com.amadeus.session.servlet.RepositoryBackedHttpSession;
import com.amadeus.session.servlet.ServletContextDescriptor;
import com.amadeus.session.servlet.ServletLevel;
import com.amadeus.session.servlet.SessionPropagation;
import com.amadeus.session.servlet.ShutdownListener;
import com.amadeus.session.servlet.WebXmlParser;
import com.amadeus.session.wrappers.HttpRequestWrapper;
import com.amadeus.session.wrappers.HttpRequestWrapperServlet3;
import com.amadeus.session.wrappers.HttpResponseWrapper;
import com.amadeus.session.wrappers.HttpResponseWrapper31;

/**
 * This class contains various methods that are called either from session
 * enabled filters, or from code injected by <code>SessionAgent</code>.
 */
public class HttpRequestResponseProcessorImpl implements HttpRequestResponseProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestResponseProcessorImpl.class);

    static final String DUMMY_ATTRIBUTE = "com.amadeus.session.dummy";
    static final String SESSION_HELPER_METHODS = "com.amadeus.session.servlet.SessionHelpers.methods";
    static final String DEFAULT_REPOSITORY_FACTORY = "com.amadeus.session.repository.inmemory.InMemoryRepositoryFactory";
    static final String INTROSPECTING_LISTENERS = "com.amadeus.session.introspected.listeners";
    static final String REQUEST_WRAPPED_ATTRIBUTE = HttpRequestWrapper.class.getName();
    static final String SESSION_CONFIGURATION = SessionConfiguration.class.getName();
    static final String SESSION_HELPERS = HttpRequestResponseProcessorImpl.class.getName();

    private boolean interceptListeners;

    private static final String DISABLED_SESSION = "com.amadeus.session.disabled";

    static boolean disabled = Boolean.parseBoolean(getPropertySecured(DISABLED_SESSION));

    public ServletRequest prepareRequest(HttpServletRequest request, HttpServletResponse response, ServletContext filterContext) {
        // Only execute this code once per request
        HttpRequestWrapper wrappedRequest = (HttpRequestWrapper) request.getAttribute(REQUEST_WRAPPED_ATTRIBUTE);
        ServletContext servletContext = filterContext;
        if (servletContext == null) {
            LOGGER.info("ServletContext was null when prepareRequest() was called from Filter. "
                    + "This means that filter's init() method was not called at initialization time. "
                    + "This may result in unexpected behavior if this filter was invoked after"
                    + "servlet dispatch forward() and include() calls.");
            servletContext = ((HttpServletRequest) request).getServletContext();
        }

        // for following we actually test that servlet context points to same
        // references, and so we don't use equals() method
        if (wrappedRequest == null || servletContext != wrappedRequest.getServletContext()) { // NOSONAR
            if (interceptListeners) {
                findListenersByIntercepting(servletContext, (HttpServletRequest) request);
            }
            wrappedRequest = wrapRequest(request, servletContext);
            wrappedRequest.setResponse(wrapResponse(response, wrappedRequest));
            request.setAttribute(REQUEST_WRAPPED_ATTRIBUTE, wrappedRequest);
            return wrappedRequest;
        }

        return request;
    }

    public ServletResponse prepareResponse(HttpServletRequest request, HttpServletResponse response, ServletContext filterContext) {
        return ((HttpRequestWrapper) request).getResponse();
    }

    public void initSessionManagement(ServletContext servletContext) {
        MethodHandle[] methods = (MethodHandle[]) servletContext.getAttribute(SESSION_HELPER_METHODS);
        if (methods == null) {
            synchronized (this) {
                methods = prepareMethodCalls(servletContext);
            }
            servletContext.setAttribute(SESSION_HELPERS, this);
            ServletContextDescriptor scd = getDescriptor(servletContext);
            setupContext(servletContext);
            SessionNotifier notifier = new HttpSessionNotifier(scd);
            SessionFactory factory = new HttpSessionFactory(servletContext);
            SessionConfiguration conf = initConf(servletContext);
            SessionRepository repository = repository(servletContext, conf);
            SessionTracking tracking = getTracking(servletContext, conf);

            ExecutorFacade executors = new ExecutorFacade(conf);

            ClassLoader classLoader = classLoader(servletContext);
            SessionManager sessionManagement = new SessionManager(executors, factory, repository, tracking, notifier,
                    conf, classLoader);
            interceptListeners = conf.isInterceptListeners();
            servletContext.setAttribute(Attributes.SESSION_MANAGER, sessionManagement);
        }
    }

    public void commitRequest(ServletRequest request, ServletRequest oldRequest, ServletContext filterContext) {
        // we are looking for identity below
        if (request != oldRequest && request instanceof HttpRequestWrapper) { // NOSONAR
            HttpRequestWrapper httpRequestWrapper = (HttpRequestWrapper) request;
            try {
                httpRequestWrapper.commit();
            } catch (Exception e) { // NOSONAR
                // Recover from any exception and log it
                LOGGER.error("An exception occured while commiting the session.", e);
            } finally {
                request.setAttribute(REQUEST_WRAPPED_ATTRIBUTE, httpRequestWrapper.getEmbeddedRequest());
            }
        }
    }

    public void interceptHttpListener(Object listener, HttpSessionEvent event) {
        if (!(listener instanceof EventListener)) {
            LOGGER.error("Tried registering listener {} but it was not EventListener", listener);
            return;
        }

        if (event.getSession() instanceof RepositoryBackedHttpSession) {
            return;
        }
        Object value = event.getSession().getServletContext().getAttribute(INTROSPECTING_LISTENERS);
        if (value != null && !((Set<?>) value).contains(listener)) {
            ((Set<Object>) value).add(listener);
            onAddListener(event.getSession().getServletContext(), listener);
        }
    }

    public void onAddListener(Object object, Object listener) {
        if (!(object instanceof ServletContext)) {
            LOGGER.error("Tried registering listener {} for object {} but object was not ServletContext", listener,
                    object);
            return;
        }
        ServletContext servletContext = (ServletContext) object;
        String contextPath = servletContext.getContextPath();
        ServletContextDescriptor scd = getDescriptor(servletContext);
        LOGGER.debug("Registering listener {} for context {}", listener, contextPath);
        // As theoretically one class can implement many listener interfaces we
        // check if it implements each of supported ones
        if (listener instanceof HttpSessionListener) {
            scd.addHttpSessionListener((HttpSessionListener) listener);
        }
        if (listener instanceof HttpSessionAttributeListener) {
            scd.addHttpSessionAttributeListener((HttpSessionAttributeListener) listener);
        }
        if (ServletLevel.isServlet31) {
            // Guard the code inside block to avoid use of classes
            // that are not available in versions before Servlet 3.1
            if (listener instanceof HttpSessionIdListener) { // NOSONAR
                scd.addHttpSessionIdListener((HttpSessionIdListener) listener);
            }
        }
    }

    private static String getPropertySecured(String key) {
        try {
            return System.getProperty(key, null);
        } catch (SecurityException e) {
            LOGGER.info("Security exception when trying to get system property", e);
            return null;
        }
    }

    private static HttpResponseWrapper wrapResponse(ServletResponse response, HttpRequestWrapper wrappedRequest) {
        if (ServletLevel.isServlet31) {
            return new HttpResponseWrapper31(wrappedRequest, (HttpServletResponse) response);
        }
        return new HttpResponseWrapper(wrappedRequest, (HttpServletResponse) response);
    }

    private static HttpRequestWrapper wrapRequest(ServletRequest request, ServletContext servletContext) {
        if (ServletLevel.isServlet3) {
            return new HttpRequestWrapperServlet3((HttpServletRequest) request, servletContext);
        }
        return new HttpRequestWrapper((HttpServletRequest) request, servletContext);
    }

    private MethodHandle[] prepareMethodCalls(ServletContext servletContext) {
        try {
            MethodHandle[] methods = (MethodHandle[]) servletContext.getAttribute(SESSION_HELPER_METHODS);
            if (methods != null) {
                return methods;
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = methodType(void.class, ServletContext.class, Object.class);
            MethodHandle onAddListner = lookup.bind(this, "onAddListener", mt);
            mt = methodType(void.class, EventListener.class, HttpSessionEvent.class);
            MethodHandle interceptHttpListener = lookup.bind(this, "interceptHttpListener", mt);
            mt = methodType(MethodHandle[].class, ServletContext.class);
            MethodHandle initSessionManagement = lookup.bind(this, "initSessionManagement", mt);
            mt = methodType(ServletResponse.class, ServletRequest.class, ServletResponse.class);
            MethodHandle prepareResponse = lookup.bind(this, "prepareResponse", mt);
            mt = methodType(ServletRequest.class, ServletRequest.class, ServletResponse.class, ServletContext.class);
            MethodHandle prepareRequest = lookup.bind(this, "prepareRequest", mt);
            mt = methodType(void.class, ServletRequest.class, ServletRequest.class);
            MethodHandle commitRequest = lookup.bind(this, "commitRequest", mt);
            methods = new MethodHandle[] { onAddListner, interceptHttpListener, initSessionManagement, prepareResponse,
                    prepareRequest, commitRequest };
            servletContext.setAttribute(SESSION_HELPER_METHODS, methods);
            return methods;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to introspect class " + HttpRequestResponseProcessorImpl.class, e);
        }
    }

    static SessionTracking getTracking(ServletContext servletContext, SessionConfiguration sessionConfiguration) {
        String sessionTracking = sessionConfiguration.getSessionTracking();
        try {
            SessionTracking instance;
            if (sessionTracking == null) {
                instance = SessionPropagation.DEFAULT.get();
            } else {
                instance = trackingFromEnum(sessionTracking);
                if (instance == null) {
                    instance = (SessionTracking) newInstance(servletContext, sessionTracking);
                }
            }
            instance.configure(sessionConfiguration);
            return instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Unable to load or instantiate SessionTracking class " + sessionTracking,
                    e);
        }
    }

    static SessionTracking trackingFromEnum(String sessionTracking)
            throws InstantiationException, IllegalAccessException {
        try {
            SessionPropagation sp = SessionPropagation.valueOf(sessionTracking);
            return sp.get();
        } catch (IllegalArgumentException e) {
            LOGGER.debug("The argument for session propagation was not enumration, "
                    + "it is probably class name: {}, message: {}", sessionTracking, e);
            return null;
        }
    }

    static SessionRepository repository(ServletContext servletContext, SessionConfiguration conf) {
        String repositoryFactoryId = conf.getRepositoryFactory();
        if (repositoryFactoryId == null) {
            repositoryFactoryId = DEFAULT_REPOSITORY_FACTORY;
        }
        @SuppressWarnings("unchecked")
        Map<String, String> providers = (Map<String, String>) servletContext.getAttribute(Attributes.PROVIDERS);
        String repositoryFactory;
        if (providers != null && providers.containsKey(repositoryFactoryId)) {
            repositoryFactory = providers.get(repositoryFactoryId);
        } else {
            repositoryFactory = repositoryFactoryId;
        }
        try {
            return repositoryOrDefault(repositoryFactory, servletContext, conf);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Unable to load or instantiate SessionRepositoryFactory. Id="
                    + repositoryFactoryId + ", Implementation=" + repositoryFactory, e);
        }
    }

    static SessionRepository repositoryOrDefault(String repositoryFactory, ServletContext context,
            SessionConfiguration conf) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        SessionRepositoryFactory instance = (SessionRepositoryFactory) newInstance(context, repositoryFactory);
        if (!conf.isDistributable() && instance.isDistributed()) {
            if (!conf.isForceDistributable()) {
                LOGGER.info("Web application {} was not marked distrubutablem using InMemoryRepository.",
                        context.getContextPath());
                return new InMemoryRepository(conf.getNamespace());
            } else {
                LOGGER.warn(
                        "Web application {} was not marked distributable, "
                                + "but the repository factory {} enforces distribution.",
                        context.getContextPath(), instance);
            }
        }
        return instance.repository(conf);
    }

    private static Object newInstance(ServletContext servletContext, String implementationClass)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> clazz = classLoader(servletContext).loadClass(implementationClass);
        return clazz.newInstance();
    }

    private static ClassLoader classLoader(ServletContext servletContext) {
        if (ServletLevel.isServlet3) {
            ClassLoader classLoader = servletContext.getClassLoader();
            if (classLoader != null) {
                return classLoader;
            }
        }
        return Thread.currentThread().getContextClassLoader();
    }

    static SessionConfiguration initConf(final ServletContext context) {
        SessionConfiguration sessionConfiguration = (SessionConfiguration) context.getAttribute(SESSION_CONFIGURATION);
        if (sessionConfiguration == null) {
            sessionConfiguration = new SessionConfiguration();
            context.setAttribute(SESSION_CONFIGURATION, sessionConfiguration);
            WebXmlParser.parseWebXml(sessionConfiguration, context);
            sessionConfiguration.initializeFrom(new SessionConfiguration.AttributeProvider() {
                @Override
                public String getAttribute(String key) {
                    return context.getInitParameter(key);
                }

                @Override
                public Object source() {
                    return context.getContextPath();
                }
            });
            // If namespace was not available, set namespace to context path of
            // the
            // webapp.
            if (sessionConfiguration.getTrueNamespace() == null) {
                sessionConfiguration.setNamespace(context.getContextPath());
            }
        }
        return sessionConfiguration;
    }

    static void findListenersByIntercepting(ServletContext context, HttpServletRequest request) {
        if (context.getAttribute(INTROSPECTING_LISTENERS) == null) {
            // If we haven't started or completed introspecting listeners, let's
            // do it
            LOGGER.info("Started collecting servlet listeners.");
            // We put a Set that will contain all introspected listeners in
            // servlet
            // context attribute
            context.setAttribute(INTROSPECTING_LISTENERS, new HashSet<Object>());
            // Then we create session inside container (not "our" session).
            // This should trigger all HttpSessionListeners
            HttpSession session = request.getSession();
            // Next we add attribute. This should trigger all
            // HttpSessionAttributeListeners
            session.setAttribute(DUMMY_ATTRIBUTE, DUMMY_ATTRIBUTE);
            session.removeAttribute(DUMMY_ATTRIBUTE);
            // Finally we remove this session.
            session.invalidate();
            // And we mark that introspecting was done.
            context.setAttribute(INTROSPECTING_LISTENERS, Boolean.TRUE);
            LOGGER.info("Finished collecting listeners.");
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
    private ServletContextDescriptor getDescriptor(ServletContext servletContext) {
        ServletContextDescriptor scd = (ServletContextDescriptor) servletContext
                .getAttribute(Attributes.SERVLET_CONTEXT_DESCRIPTOR);
        if (scd == null) {
            scd = new ServletContextDescriptor(servletContext);
            servletContext.setAttribute(Attributes.SERVLET_CONTEXT_DESCRIPTOR, scd);
            LOGGER.info("Registered servlet context {}.", servletContext.getContextPath());
        }
        return scd;
    }

    /**
     * Sets up servlet context - registers {@link SessionFilter} and
     * {@link ShutdownListener}.
     *
     * @param context
     *            servlet context to set up
     */
    static void setupContext(ServletContext context) {
        if (ServletLevel.isServlet3) {
            // When using Servlet 3.x+, we will register SessionFilter to make
            // sure session replacement is enabled
            Dynamic reg = context.addFilter("com.amdeus.session.filter", new SessionFilter());
            if (reg != null) {
                // The filter applies to all requests
                reg.addMappingForUrlPatterns(null, false, "/*");
            }
            // At the web app shutdown, we need to do some cleanup
            context.addListener(new ShutdownListener());
        }
    }
}
