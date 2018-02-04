package com.test.session.api;

import javax.servlet.http.HttpServletResponse;

/**
 * A service for extracting session id from request, propagating session into
 * response or generating new session id.
 */
public interface SessionTracking {

    /**
     * Retrieves session id from the request
     *
     * @param request the current request object
     * @return session id or null if id is present in the request
     */
    String retrieveId(RequestWithSession request);

    /**
     * Propagates session to client. Implementation must allow multiple
     * idempotent calls for same request.
     *
     * @param request the current request object
     * @param response the response object
     */
    void propagateSession(String sessionId, HttpServletResponse response);

    /**
     * Generates new session id.
     *
     * @return new session id
     */
    String newId();

    /**
     * Encodes passed URL adding session if needed.
     *
     * @param request the current request object
     * @param url the URL to encode
     * @return encoded URL
     */
    String encodeUrl(String sessionId, String url);
}
