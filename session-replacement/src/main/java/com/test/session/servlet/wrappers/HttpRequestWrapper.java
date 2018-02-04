package com.test.session.servlet.wrappers;

import java.util.Optional;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.api.RequestWithSession;
import com.test.session.api.SessionManager;
import com.test.session.models.SessionConstants;
import com.test.session.servlet.RepositoryBackedHttpSession;

/**
 * Wrapper for {@link HttpServletRequest} that implements storing of sessions in
 * repository. This class implements following commit logic: propagate session
 * to response, store session in repository, perform cleanups as request
 * processing has finished.
 */
public class HttpRequestWrapper extends HttpServletRequestWrapper implements RequestWithSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestWrapper.class);

    private final HttpRequestWrapper embeddedRequest;
    private final SessionManager manager;

    private RepositoryBackedHttpSession session;
    private boolean idRetrieved;
    private String retrievedId;
    private HttpResponseWrapper response;
    private boolean committed;
    private boolean async;
    private boolean propagateOnCreate;
    private boolean propagated;
    private boolean repositoryChecked;

    public HttpRequestWrapper(HttpServletRequest req, SessionManager sessionManager) {
        super(req);

        manager = sessionManager;
        ServletRequest originalRequest = req;

        while (originalRequest instanceof ServletRequestWrapper) {
            if (originalRequest instanceof HttpRequestWrapper) {
                break;
            }

            originalRequest = ((ServletRequestWrapper) originalRequest).getRequest();
        }

        embeddedRequest = (originalRequest instanceof HttpRequestWrapper) ? (HttpRequestWrapper) originalRequest : null;
    }

    @Override
    public RepositoryBackedHttpSession getSession() {
        try {
            return getSession(true);
        } catch (Exception ex) {
            // Do nothing
        }

        return null;
    }

    @Override
    public RepositoryBackedHttpSession getSession(boolean create) {
        LOGGER.debug("Getting session with create set to {}", create);

        if (embeddedRequest != null) {
            // If there is embedded HttpRequestWrapper, create session there if needed
            LOGGER.debug("Delegating the task to the embeded request.");
            embeddedRequest.getSession(create);
        }

        try {
            return getRepositoryBackedSession(create);
        } catch (Exception ex) {
            // Do nothing
        }

        return null;
    }

    @Override
    public AsyncContext startAsync() {
        LOGGER.debug("Start Async operation.");

        AsyncContext ac = super.startAsync();
        SessionCommitListener listener = (e) -> doCommit();

        ac.addListener(listener);
        async = true;

        return ac;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        LOGGER.debug("Start Async operation.");

        AsyncContext ac = super.startAsync(servletRequest, servletResponse);
        SessionCommitListener listener = (e) -> doCommit();

        ac.addListener(listener);
        async = true;

        return ac;
    }

    @Override
    public String getRequestedSessionId() {
        LOGGER.debug("Checking if the ID retrieved earlier and with the request wrapper. idRetrieved {}", idRetrieved);

        if (!idRetrieved) {
            getSession(false);
        }

        LOGGER.debug("Retrieved ID {}", retrievedId);
        return retrievedId;
    }

    @Override
    public RepositoryBackedHttpSession getRepositoryBackedSession(boolean create) {
        LOGGER.debug("Getting session with create set to {} session committed {}", create, committed);

        if (committed) {
            throw new IllegalStateException("Session was already committed.");
        }

        retrieveSessionIfNeeded(create);

        // Only propagate session if this is the "outer" session, 
        // i.e. the one that created the session closest to the client
        if ((session == null) && propagateOnCreate) {
            doPropagateAndStoreIfFirstWrapper();
        }

        return session;
    }

    @Override
    public boolean isIdRetrieved() {
        LOGGER.debug("Is ID already retrieved {}", idRetrieved);
        return idRetrieved;
    }

    @Override
    public void setRequestedSessionId(String id) {
        LOGGER.debug("Got the requested session ID {}, setting the retrievedId flag as true.", id);

        idRetrieved = true;
        retrievedId = id;
    }

    @Override
    public boolean isRepositoryChecked() {
        LOGGER.debug("Is the repository already checked {}", repositoryChecked);
        return repositoryChecked;
    }

    @Override
    public void repositoryChecked() {
        LOGGER.debug("Already checked the repository for the session and setting the flag.");
        repositoryChecked = true;
    }

    public void commit() {
        LOGGER.debug("Committing the Session. Async mode {}", async);

        try {
            if (!async) {
                doCommit();
            }
        } catch (Exception ex) {
            // Do nothing
        }
    }

    public HttpResponseWrapper getResponse() {
        return response;
    }

    public void setResponse(HttpResponseWrapper response) {
        this.response = response;
    }

    boolean propagateSession() {
        LOGGER.debug("Starting propagate session. Already committed {}", committed);

        try {
            if (committed && !isDirty()) {
                LOGGER.debug("It is a clean and committed session. So assumed propagated.");
                return true;
            }

            RepositoryBackedHttpSession session = getRepositoryBackedSession(false);

            if (session == null) {
                LOGGER.debug("No existing session found. So no need to propagate now.");

                setPropagateOnCreate(true);
                return false;
            }

            LOGGER.debug("Check if the first wapper and then propagate. Session {}", session);
            return doPropagateAndStoreIfFirstWrapper();
        } catch (Exception ex) {
            // Do nothing
        }

        return false;
    }

    String encodeURL(String url) {
        LOGGER.debug("Encoding the URL {}", url);

        RepositoryBackedHttpSession session = (embeddedRequest != null) 
                ? embeddedRequest.getRepositoryBackedSession(false)
                : getRepositoryBackedSession(false);

        LOGGER.debug("Retrieved session {}", session);        

        if (session == null || !session.isValid()) {
            LOGGER.debug("Session is either null or not valid. No need to do the encode with session.");
            return url;
        }

        LOGGER.debug("Delegating to session manager to do the encoding.");
        return manager.encodeUrl(session.getId(), url);
    }

    private boolean doPropagateAndStoreIfFirstWrapper() {
        boolean dirty = isDirty();
        LOGGER.debug("Propagate only if it is first wrapper {}, already propagated {}, isDirty {}", (embeddedRequest == null), propagated, dirty);

        if (embeddedRequest == null && (!propagated || dirty)) {
            
            if (getAttribute(SessionConstants.SESSION_PROPAGATED) == null) {
                setAttribute(SessionConstants.SESSION_PROPAGATED, Boolean.TRUE);

                RepositoryBackedHttpSession session = getRepositoryBackedSession(false);

                if (session != null && !session.isValid()) {
                    session = null;
                }

                // If the session is null, make sure we are deleting the cookie
                manager.propagateSession((session == null) ? null : session.getId(), response);
            }

            storeSession();

            LOGGER.debug("Setting already propagated.");
            propagated = true;
            return true;
        }

        LOGGER.debug("Not propagating the session.");
        return false;
    }

    private boolean isDirty() {
        return session != null && session.isDirty();
    }

    private void retrieveSessionIfNeeded(boolean create) {
        LOGGER.debug("Get or create session. isCreate {}", create);

        if (embeddedRequest != null) {
            LOGGER.debug("Embeded request present. Delegating.");
            embeddedRequest.retrieveSessionIfNeeded(create);
        }

        if (session == null || !session.isValid()) {
            LOGGER.debug("Either session is not present or the session is not valid.");
            session = (RepositoryBackedHttpSession) manager.getSession(this, create, getEmbededdSessionId());
        }
    }

    private String getEmbededdSessionId() {
        LOGGER.debug("Trying to get the session ID from embeded request.");

        Optional<String> sessionId = Optional.ofNullable(embeddedRequest)
                .filter(req -> (req.session != null))
                .map(req -> req.session)
                .filter(sess -> sess.isValid())
                .map(sess -> sess.getId());

        if (sessionId != null && sessionId.isPresent()) {
            return sessionId.get();
        }

        return null;
    }

    private void doCommit() {
        LOGGER.debug("Starting commit. Already committed {}", committed);

        if (committed) {
            return;
        }

        // we propagate the session, and that will trigger storage
        if (!propagateSession()) {
            LOGGER.debug("Not propagating session.");
            storeSession();
        }

        committed = true;
        session.requestFinished();
    }

    private void storeSession() {
        LOGGER.debug("Starting store session.");
        retrieveSessionIfNeeded(false);

        if (session != null) {
            LOGGER.debug("Session exists. So committing.");

            try {
                session.commit();
            } catch (Exception e) {
                LOGGER.warn("cannot store session: {}", session, e);
            }

            if (session.canRemoveFromCache()) {
                // manager.committed(session.getId());
            }
        } else {
            LOGGER.debug("session was null, nothing to commit");
        }
    }

    /**
     * Controls if session should be propagated on create. Session should be
     * propagated on request if {@link #propagateSession()} method was called
     * (i.e. if there an event occurred that requires session propagation).
     * 
     * @param propagate
     */
    private void setPropagateOnCreate(boolean propagate) {
        this.propagateOnCreate = propagate;

        if (embeddedRequest != null) {
            LOGGER.debug("Since the embeded request exists, setting propagateOnCreate as true.");
            embeddedRequest.setPropagateOnCreate(true);
        }
    }

    // This is for Servlet 3.1. When this code will be migrate to AEM 6.3, this portion will be required.
    // @Override
    // public String changeSessionId() {
    // retrieveSessionIfNeeded(false);
    // if (session == null) {
    // throw new IllegalStateException("There is no session associated with the
    // request.");
    // }
    // manager.switchSessionId(session);
    // return session.getId();
    // }

    /**
     * Callback for async requests that performs commit when async processing
     * has been completed.
     */
    @FunctionalInterface
    private interface SessionCommitListener extends AsyncListener {

        default void onError(AsyncEvent event) {
            LOGGER.debug("Error while executing async event.");
        }

        default void onStartAsync(AsyncEvent event) {
            LOGGER.debug("Async event started.");
        }

        default void onTimeout(AsyncEvent event) {
            LOGGER.debug("Async event timed out.");
        }
    }
}
