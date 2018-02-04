package com.test.session.manager;

import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.session.TaskExecutorProcess;
import com.test.session.api.OSGiDependencyService;
import com.test.session.api.RequestWithSession;
import com.test.session.api.SessionConfigurationService;
import com.test.session.api.SessionManager;
import com.test.session.models.SessionData;
import com.test.session.servlet.RepositoryBackedHttpSession;

/**
 * Main class responsible for managing sessions. The class offers strategy for
 * retrieving, creating, propagating and deleting session. It also offers
 * services for scheduling and executing task asynchronously.
 * <p>
 * In case of servlet engines, one session manager will be created per
 * {@link ServletContext}.
 * <p>
 * The manager provides following metrics:
 * <ul>
 * <li>`com.test.session.created` measures total number of created sessions as
 * well as rate of sessions created in last 1, 5 and 15 minutes
 * <li>`com.test.session.deleted` measures total number of deleted sessions as
 * well as rate of sessions measures rate of sessions deleted in last 1, 5 and
 * 15 minutes
 * <li>`com.test.session.missing` measures total number of session which were
 * not found in repository, as measures rate of such occurrences in last 1, 5
 * and 15 minutes
 * <li>`com.test.session.retrieved` measures total number of session retrievals
 * as well as measures rate of sessions retrieval from store in last 1, 5 and 15
 * minutes
 * <li>`com.test.session.timers.commit` measures histogram (distribution) of
 * elapsed time during commit as well as total number of commits and rate of
 * commits over last 1, 5 and 15 minutes
 * <li>`com.test.session.timers.fetch` measures histogram (distribution) of
 * elapsed time during fetch of session data from repository as well as total
 * number of fetch requests and rate of fetch requests over last 1, 5 and 15
 * minutes
 * </ul>
 */
@Component(immediate = true)
@Service
public class SessionManagerServiceImpl implements SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManagerServiceImpl.class);

    @Reference
    private SessionConfigurationService configuration;

    @Reference
    private OSGiDependencyService dependencyService;

    private ServletContext servletContext;

    @Deactivate
    protected void close(Map<String, ?> properties) {
        LOGGER.debug("Closing service. Stopping the task executor.");
        TaskExecutorProcess.getInstance(configuration).close();
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        LOGGER.debug("First call to session manager to set the servlet context.");
        this.servletContext = servletContext;
    }

    @Override
    public RepositoryBackedHttpSession getSession(RequestWithSession request, boolean create, String forceId) {
        LOGGER.debug("Retrieving session with create {} and forceId {}", create, forceId);

        String id = forceId;
        RepositoryBackedHttpSession session = null;

        LOGGER.debug("Retrieving existing session ID with forceId {}", forceId);

        if (StringUtils.isBlank(forceId)) {
            LOGGER.debug("Since forceId is null, checking from request otherwise from the tracking method.");
            id = request.isIdRetrieved() 
                    ? request.getRequestedSessionId() 
                    : dependencyService.getSessionTrackingMethod().retrieveId(request);;
        }

        LOGGER.debug("Retrieved ID {}", id);
        request.setRequestedSessionId(id);

        if (StringUtils.isNotBlank(id)) {
            session = fetchSession(id, true);

            if (session == null && !request.isRepositoryChecked()) {
                LOGGER.debug("Session with sessionId: '{}' is null and it was not in repository.", id);
                request.repositoryChecked();
            }
        }

        if (session == null && create) {
            LOGGER.debug("No session found and application has asked to create a new session.");
            id = StringUtils.defaultString(forceId, dependencyService.getSessionTrackingMethod().newId());

            LOGGER.debug("Creating new session with sessionId: '{}' and storing the same in the repository.", id);

            session = newSessionObject(new SessionData(id, configuration.getMaxInactiveInterval(), true), servletContext);
            session.storeSessionData();
        }

        if (session != null) {
            session.checkUsedAndLock();
        }

        return session;
    }

    @Override
    public void propagateSession(String sessionId, HttpServletResponse response) {
        dependencyService.getSessionTrackingMethod().propagateSession(sessionId, response);
    }

    @Override
    public void delete(SessionData sessionData, boolean expired) {
        LOGGER.debug("Deleting the session {}. ALready expired {}", sessionData, expired);
        RepositoryBackedHttpSession session = fetchSession(sessionData.getId(), false);

        if (session != null) {
            LOGGER.debug("Found the session. Invalidating.");
            session.doInvalidate(expired);
        } else if (!expired) {
            LOGGER.debug("Session not found in repository for sessionId: '{}'", sessionData.getId());
        }
    }

    @Override
    public void deleteAsync(SessionData session, final boolean expired) {
        Runnable task = () -> delete(session, expired);
        TaskExecutorProcess.getInstance(configuration).submit(task, false, 0, 0, null);
    }

    @Override
    public String encodeUrl(String sessionId, String url) {
        return dependencyService.getSessionTrackingMethod().encodeUrl(sessionId, url);
    }

    @Override
    public void switchSessionId(RepositoryBackedHttpSession session) {
        LOGGER.debug("This functionality is for Servlet 3.1 container and not yet implemented.");
    }

    private RepositoryBackedHttpSession newSessionObject(SessionData sessionData, ServletContext servletContext) {
        return new RepositoryBackedHttpSession(servletContext, sessionData, 
                configuration.isReplicationTrigger(), configuration.getRepositoryFactory());
    }

    private RepositoryBackedHttpSession fetchSession(String sessionId, boolean updateTimestamp) {
        LOGGER.debug("Retrieving session with sessionId {} and updateTimestamp {}", sessionId, updateTimestamp);
        RepositoryBackedHttpSession session = findSessionWithSessionId(sessionId);

        if (session == null) {
            LOGGER.debug("Session was not found, considered expired or invalid, sessionId: {}", sessionId);
            return null;
        }

        SessionData sessionData = session.getSessionData();

        sessionData.setNew(false);

        if (updateTimestamp) {
            sessionData.setLastAccessedTime(System.currentTimeMillis());
        }

        // Build session from factory
        session = newSessionObject(sessionData, servletContext);
        LOGGER.debug("Session created {}", session);

        if (session.isExpired()) {
            LOGGER.debug("Session was present, but it was expired. So invalidate the session and return null.");

            session.doInvalidate(true);
            return null;
        }

        LOGGER.debug("Updating the session timestamp in the repository.");
        session.storeSessionData();

        return session;
    }

    private RepositoryBackedHttpSession findSessionWithSessionId(String sessionId) {
        LOGGER.debug("Fetching session associated with sessionId: {}", sessionId);
        RepositoryBackedHttpSession session = null;

        LOGGER.debug("Searching the session in the repository.");

        RepositoryBackedHttpSession temp = newSessionObject(new SessionData(sessionId, configuration.getMaxInactiveInterval()), servletContext);
        SessionData data = temp.getSessionDataById();

        if (data == null) {
            LOGGER.debug("No session found in the repository as well. Returning null.");
            return null;
        }

        session = newSessionObject(data, servletContext);
        LOGGER.debug("Session data found in the repository. Storing the session {} back in the cache if enabled.", session);

        return session;
    }
}
