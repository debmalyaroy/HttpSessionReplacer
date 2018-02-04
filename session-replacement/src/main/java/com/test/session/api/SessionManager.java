package com.test.session.api;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

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
public interface SessionManager {

    /**
     * Builds or retrieves session. If session is found in repository, it is
     * retrieved, if not, and create parameter is set to <code>true</code>, then
     * a new session is created. Session id is generated according to
     * {@link SessionTracking} implementation.
     * <p>
     * In some cases, in servlet engine, request can be forwarded from one web
     * application to another one. In this case, the first web application that
     * received request, is responsible for managing session id, and other web
     * application down the chain will reuse this id.
     *
     * @param request
     *            the request being servet
     * @param create
     *            <code>true</code> if session should be created
     * @param forceId
     *            forces usage of this session id.
     *
     * @return existing or new session
     */
    RepositoryBackedHttpSession getSession(RequestWithSession request, boolean create, String forceId);

    /**
     * Propagates the session id to the response. The propagation is done once
     * per request.
     *
     * @param request
     *            the current request
     * @param response
     *            the current response
     */
    void propagateSession(String sessionId, HttpServletResponse response);

    /**
     * Deletes session from repository and performs orderly cleanup. Called when
     * session expires or when application closes
     *
     * @param sessionId
     *            the id of the session to delete
     * @param expired
     *            <code>true</code> if session is deleted because it has expired
     */
    void delete(SessionData session, boolean expired);

    /**
     * Deletes list of sessions. The deletion might be run in separate thread.
     *
     * @param sessionId
     *            session id to delete
     * @param expired
     *            <code>true</code> if session is deleted because it has expired
     */
    void deleteAsync(SessionData session, final boolean expired);

    /**
     * Called to encode URL based on session tracking.
     *
     * @param request
     *            the current request
     * @param url
     *            the URL to encode
     * @return encoded URL
     */
    String encodeUrl(String sessionId, String url);

    /**
     * Changes session id of the passed session. Session id can change only once
     * per request.
     *
     * @param session
     *            the session whose id needs to change
     */
    void switchSessionId(RepositoryBackedHttpSession session);

    void setServletContext(ServletContext servletContext);
}
